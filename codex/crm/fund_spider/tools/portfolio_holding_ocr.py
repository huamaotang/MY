from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import asdict, dataclass
from datetime import date
from pathlib import Path
from statistics import mean
from typing import Any, Iterable


EXACT_NOISE = {
    "全部",
    "全部交易",
    "所有月份",
    "偏股",
    "偏债",
    "指数",
    "黄金",
    "全球",
    "名称",
    "金额/昨日收益",
    "持有收益/率",
    "我的持有",
    "持有收益率排序",
    "金选指数基金",
    "基金市场",
    "机会",
    "自选",
    "持有",
    "筛选",
    "详情",
    "资产明细",
    "交易明细",
    "交易记录",
    "明细",
}

NOISE_SUBSTRINGS = {
    "更多产品，去市场看看",
    "基金销售服务由蚂蚁",
    "本页面非任何法律文件",
    "该页面由蚂蚁财富平台设计",
    "根据你的持仓情况",
    "不构成任何未来的收益预期",
    "产品解读",
}

FAILED_TOKENS = ("交易关闭", "失败", "撤销", "已撤销", "取消", "已取消")
SUCCESS_TOKENS = ("买入成功", "卖出成功", "交易成功", "已完成", "确认成功")
DATETIME_PATTERN = re.compile(r"(20\d{2})[-/.年](\d{1,2})[-/.月](\d{1,2})[日\s]*(\d{1,2}):(\d{2})(?::(\d{2}))?")


@dataclass
class OcrBlock:
    text: str
    score: float
    x0: float
    y0: float
    x1: float
    y1: float


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--json", action="store_true", help="Print JSON output")
    parser.add_argument("--image", action="append", required=True, help="Image path")
    parser.add_argument("--screenshot-date", help="Override screenshot date (YYYY-MM-DD)")
    parser.add_argument(
        "--source-label",
        choices=("alipay", "tencent"),
        default="alipay",
        help="Portfolio account source",
    )
    parser.add_argument(
        "--import-type",
        choices=("holding", "trade"),
        default="holding",
        help="Screenshot import type",
    )
    args = parser.parse_args()

    try:
        from rapidocr_onnxruntime import RapidOCR
    except Exception as exc:  # pragma: no cover - depends on the deployment runtime
        raise SystemExit(
            "rapidocr-onnxruntime is required. Install fund_spider/requirements.txt "
            "and point CRM_PYTHON_EXECUTABLE to that environment."
        ) from exc

    screenshot_date = args.screenshot_date or date.today().isoformat()
    engine = RapidOCR()
    images = []
    warnings: list[str] = []

    for image_path in args.image:
        image_file = Path(image_path)
        if not image_file.exists():
            raise SystemExit(f"image not found: {image_path}")

        blocks = run_ocr(engine, image_path)
        rows = parse_rows(
            blocks,
            screenshot_date,
            warnings,
            args.source_label,
            args.import_type,
        )
        images.append(
            {
                "imagePath": str(image_file),
                "rows": rows,
                "blocks": [asdict(block) for block in blocks],
            }
        )

    payload: dict[str, Any] = {"images": images, "warnings": warnings}
    if args.json:
        json.dump(payload, sys.stdout, ensure_ascii=False)
    else:
        for image in images:
            print(image["imagePath"])
            for row in image["rows"]:
                print(row)
    return 0


def run_ocr(engine: Any, image_path: str) -> list[OcrBlock]:
    result, _ = engine(image_path)
    blocks: list[OcrBlock] = []
    for item in result or []:
        box, text, score = item
        text = normalize_text(str(text))
        if not text:
            continue
        blocks.append(
            OcrBlock(
                text=text,
                score=float(score),
                x0=min(point[0] for point in box),
                y0=min(point[1] for point in box),
                x1=max(point[0] for point in box),
                y1=max(point[1] for point in box),
            )
        )
    blocks.sort(key=lambda block: (block.y0, block.x0))
    return blocks


def parse_rows(
    blocks: list[OcrBlock],
    screenshot_date: str,
    warnings: list[str],
    source_label: str = "alipay",
    import_type: str = "holding",
) -> list[dict[str, Any]]:
    if import_type == "trade":
        rows = parse_trade_rows(blocks, screenshot_date, source_label)
        if not rows:
            warnings.append(f"no {source_label} trade rows detected")
        return rows

    rows = parse_holding_rows(blocks, screenshot_date, source_label)
    if not rows:
        warnings.append(f"no {source_label} portfolio rows detected")
    return rows


def parse_holding_rows(
    blocks: list[OcrBlock], screenshot_date: str, source_label: str
) -> list[dict[str, Any]]:
    if is_account_list_layout(blocks):
        return parse_account_list_rows(blocks, screenshot_date)
    if source_label == "tencent" and any("资产明细" in block.text for block in blocks):
        return parse_tencent_holding_rows(blocks, screenshot_date)
    return parse_alipay_holding_rows(blocks, screenshot_date)


def is_account_list_layout(blocks: list[OcrBlock]) -> bool:
    texts = {block.text for block in blocks}
    return "关联板块" in texts and "持有收益" in texts


def parse_account_list_rows(
    blocks: list[OcrBlock], screenshot_date: str
) -> list[dict[str, Any]]:
    header_bottom = max(
        (
            block.y1
            for block in blocks
            if block.text in {"当日收益", "关联板块", "持有收益"}
        ),
        default=700,
    )
    candidates = [
        block
        for block in blocks
        if block.y0 > header_bottom + 40
        and block.x0 < 540
        and looks_like_name(block.text)
        and "新增持有" not in block.text
        and not block.text.startswith(("￥", "¥"))
    ]
    rows: list[dict[str, Any]] = []
    for name in sorted(candidates, key=lambda block: block.y0):
        group = [
            block
            for block in blocks
            if name.y0 - 25 <= block.y0 <= name.y0 + 115 and not is_noise(block.text)
        ]
        amount = pick_account_number(group, 0, 520, name.y0 + 35, percent=False)
        holding_profit = pick_account_number(
            group, 1020, 1284, name.y0 - 25, percent=False
        )
        holding_return_rate = pick_account_number(
            group, 1080, 1284, name.y0 + 35, percent=True
        )
        if amount is None and holding_profit is None and holding_return_rate is None:
            continue
        rows.append(
            holding_row(
                name.text.rstrip(".…"),
                amount,
                holding_profit,
                holding_return_rate,
                None,
                group,
                screenshot_date,
            )
        )
    return rows


def parse_alipay_holding_rows(
    blocks: list[OcrBlock], screenshot_date: str
) -> list[dict[str, Any]]:
    candidates = [
        block
        for block in blocks
        if 600 <= block.y0 <= 2300
        and block.x0 < 620
        and looks_like_name(block.text)
        and not is_noise(block.text)
    ]
    groups = groups_from_anchors(blocks, candidates, lower_bound=2450)
    rows: list[dict[str, Any]] = []
    for anchor, group in groups:
        top_numbers = [
            block
            for block in group
            if anchor.y0 - 20 <= block.y0 <= anchor.y0 + 105
            and is_number_like(block.text)
        ]
        bottom_numbers = [
            block
            for block in group
            if anchor.y0 + 35 <= block.y0 <= anchor.y0 + 150
            and is_number_like(block.text)
        ]
        amount = pick_number(
            top_numbers, prefer_x_min=620, prefer_x_max=980, prefer_percent=False
        )
        holding_profit = pick_number(
            [block for block in top_numbers if block.x0 >= 950],
            prefer_percent=False,
        )
        yesterday_profit = pick_number(
            bottom_numbers,
            prefer_x_min=620,
            prefer_x_max=980,
            prefer_percent=False,
        )
        holding_return_rate = pick_number(
            [block for block in bottom_numbers if "%" in block.text],
            prefer_percent=True,
        )
        if not any((amount, holding_profit, yesterday_profit, holding_return_rate)):
            continue
        name_parts = [
            block.text
            for block in sorted(group, key=lambda item: (item.y0, item.x0))
            if block.x0 < 620
            and anchor.y0 - 15 <= block.y0 <= anchor.y0 + 115
            and looks_like_name(block.text)
        ]
        fund_name = "".join(name_parts).strip() or anchor.text
        rows.append(
            holding_row(
                fund_name,
                amount,
                holding_profit,
                holding_return_rate,
                yesterday_profit,
                group,
                screenshot_date,
            )
        )
    return rows


def parse_tencent_holding_rows(
    blocks: list[OcrBlock], screenshot_date: str
) -> list[dict[str, Any]]:
    header_bottom = max(
        (block.y1 for block in blocks if "资产明细" in block.text),
        default=430,
    )
    service_top = min(
        (block.y0 for block in blocks if "持仓服务" in block.text),
        default=2450,
    )
    candidates = [
        block
        for block in blocks
        if header_bottom + 40 < block.y0 < service_top
        and block.x0 < 900
        and looks_like_name(block.text)
        and not any(
            token in block.text
            for token in (
                "持有金额",
                "持仓收益",
                "昨日收益",
                "按持有金额排序",
                "产品解读",
            )
        )
    ]
    groups = groups_from_anchors(blocks, candidates, lower_bound=service_top)
    rows: list[dict[str, Any]] = []
    for anchor, group in groups:
        amount = number_below_label(group, "持有金额", 0, 450)
        holding_profit = number_below_label(group, "持仓收益", 400, 900)
        yesterday_profit = number_below_label(group, "昨日收益", 850, 1285)
        if not any((amount, holding_profit, yesterday_profit)):
            continue
        rows.append(
            holding_row(
                anchor.text.rstrip(".…"),
                amount,
                holding_profit,
                None,
                yesterday_profit,
                group,
                screenshot_date,
            )
        )
    return rows


def groups_from_anchors(
    blocks: list[OcrBlock],
    anchors: list[OcrBlock],
    lower_bound: float,
) -> list[tuple[OcrBlock, list[OcrBlock]]]:
    anchors = sorted(dedupe_close_anchors(anchors), key=lambda block: block.y0)
    groups: list[tuple[OcrBlock, list[OcrBlock]]] = []
    for index, anchor in enumerate(anchors):
        end = anchors[index + 1].y0 - 20 if index + 1 < len(anchors) else lower_bound
        group = [
            block
            for block in blocks
            if anchor.y0 - 25 <= block.y0 < end and not is_noise(block.text)
        ]
        groups.append((anchor, group))
    return groups


def dedupe_close_anchors(anchors: list[OcrBlock]) -> list[OcrBlock]:
    result: list[OcrBlock] = []
    for anchor in sorted(anchors, key=lambda block: (block.y0, block.x0)):
        if result and anchor.y0 - result[-1].y0 < 55:
            continue
        result.append(anchor)
    return result


def holding_row(
    fund_name: str,
    amount: OcrBlock | None,
    holding_profit: OcrBlock | None,
    holding_return_rate: OcrBlock | None,
    yesterday_profit: OcrBlock | None,
    group: list[OcrBlock],
    screenshot_date: str,
) -> dict[str, Any]:
    confidences = [block.score for block in group if block.score > 0]
    return {
        "fundName": fund_name,
        "holdingAmount": clean_number(amount.text) if amount else None,
        "holdingProfit": clean_number(holding_profit.text)
        if holding_profit
        else None,
        "holdingReturnRate": clean_number(holding_return_rate.text)
        if holding_return_rate
        else None,
        "yesterdayProfit": clean_number(yesterday_profit.text)
        if yesterday_profit
        else None,
        "todayProfit": None,
        "holdingShares": None,
        "costNav": None,
        "screenshotDate": screenshot_date,
        "confidence": round(mean(confidences), 4) if confidences else None,
        "rawTexts": [
            block.text for block in sorted(group, key=lambda item: (item.y0, item.x0))
        ],
    }


def parse_trade_rows(
    blocks: list[OcrBlock], screenshot_date: str, source_label: str
) -> list[dict[str, Any]]:
    operations = [
        block
        for block in blocks
        if normalize_operation(block.text) is not None and block.x0 < 260
    ]
    operations = dedupe_close_anchors(operations)
    rows: list[dict[str, Any]] = []
    for index, operation_block in enumerate(operations):
        end = operations[index + 1].y0 - 20 if index + 1 < len(operations) else 2900
        group = [
            block
            for block in blocks
            if operation_block.y0 - 35 <= block.y0 < end and not is_noise(block.text)
        ]
        row = parse_trade_group(group, operation_block, screenshot_date, source_label)
        if row is not None:
            rows.append(row)
    return rows


def parse_trade_group(
    group: list[OcrBlock],
    operation_block: OcrBlock,
    screenshot_date: str,
    source_label: str,
) -> dict[str, Any] | None:
    operation_type = normalize_operation(operation_block.text)
    if operation_type is None:
        return None

    timestamp_block = next(
        (block for block in group if normalize_datetime(block.text) is not None),
        None,
    )
    amount_candidates = [
        block
        for block in group
        if block.x0 >= 820
        and is_number_like(block.text)
        and "%" not in block.text
        and block is not timestamp_block
    ]
    amount = sorted(amount_candidates, key=lambda block: (block.y0, -block.x0))[0] if amount_candidates else None

    name_candidates = [
        block
        for block in group
        if operation_block.y0 - 30 <= block.y0 <= operation_block.y0 + 145
        and 160 <= block.x0 < 950
        and looks_like_trade_name(block.text)
    ]
    if source_label == "alipay":
        preferred = [
            block for block in name_candidates if block.text.lstrip().startswith("基金")
        ]
        if preferred:
            name_candidates = preferred + [
                block for block in name_candidates if block not in preferred
            ]
    name_candidates.sort(key=lambda block: (block.y0, block.x0))
    if not name_candidates or amount is None or timestamp_block is None:
        return None

    first_name = strip_trade_name_prefix(name_candidates[0].text)
    continuation = [
        strip_trade_name_prefix(block.text)
        for block in name_candidates[1:]
        if block.y0 - name_candidates[0].y0 <= 115
        and not strip_trade_name_prefix(block.text).startswith(("银行卡", "余额宝"))
    ]
    fund_name = (first_name + "".join(continuation)).strip()
    if not fund_name:
        return None

    raw_texts = [
        block.text for block in sorted(group, key=lambda item: (item.y0, item.x0))
    ]
    transaction_status = "UNKNOWN"
    if any(token in text for token in FAILED_TOKENS for text in raw_texts):
        transaction_status = "FAILED"
    elif any(token in text for token in SUCCESS_TOKENS for text in raw_texts):
        transaction_status = "SUCCESS"
    confidences = [block.score for block in group if block.score > 0]
    return {
        "fundName": fund_name.rstrip(".…"),
        "operationType": operation_type,
        "transactionAmount": clean_number(amount.text),
        "transactionAt": normalize_datetime(timestamp_block.text),
        "transactionStatus": transaction_status,
        "screenshotDate": screenshot_date,
        "confidence": round(mean(confidences), 4) if confidences else None,
        "rawTexts": raw_texts,
    }


def normalize_operation(text: str) -> str | None:
    compact = text.replace(" ", "")
    if compact in {"买入", "申购", "定投"}:
        return "BUY"
    if compact in {"卖出", "赎回"}:
        return "SELL"
    return None


def normalize_datetime(text: str) -> str | None:
    normalized = (
        text.replace("–", "-")
        .replace("—", "-")
        .replace("－", "-")
        .replace("：", ":")
    )
    match = DATETIME_PATTERN.search(normalized)
    if not match:
        return None
    year, month, day, hour, minute, second = match.groups()
    return (
        f"{int(year):04d}-{int(month):02d}-{int(day):02d}T"
        f"{int(hour):02d}:{int(minute):02d}:{int(second or 0):02d}"
    )


def looks_like_trade_name(text: str) -> bool:
    if normalize_datetime(text) is not None or is_number_like(text) or is_noise(text):
        return False
    if normalize_operation(text) is not None:
        return False
    if any(token in text for token in FAILED_TOKENS + SUCCESS_TOKENS):
        return False
    if text.startswith(("银行卡", "余额宝", "买入成功", "卖出成功")):
        return False
    return looks_like_name(strip_trade_name_prefix(text))


def strip_trade_name_prefix(text: str) -> str:
    value = re.sub(r"^\s*基金\s*[|丨｜1一]?\s*", "", text)
    return value.strip()


def number_below_label(
    blocks: list[OcrBlock], label: str, x_min: float, x_max: float
) -> OcrBlock | None:
    labels = [block for block in blocks if label in block.text]
    if not labels:
        return None
    label_block = labels[0]
    candidates = [
        block
        for block in blocks
        if x_min <= block.x0 <= x_max
        and label_block.y0 <= block.y0 <= label_block.y0 + 150
        and is_number_like(block.text)
        and "%" not in block.text
    ]
    return (
        sorted(
            candidates,
            key=lambda block: (abs(block.x0 - label_block.x0), block.y0),
        )[0]
        if candidates
        else None
    )


def pick_account_number(
    blocks: list[OcrBlock],
    x_min: float,
    x_max: float,
    y_min: float,
    percent: bool,
) -> OcrBlock | None:
    matches = [
        block
        for block in blocks
        if x_min <= block.x0 <= x_max
        and block.y0 >= y_min
        and is_number_like(block.text)
        and (("%" in block.text) == percent)
    ]
    return sorted(matches, key=lambda block: (block.y0, block.x0))[0] if matches else None


def pick_number(
    blocks: list[OcrBlock],
    prefer_x_min: float | None = None,
    prefer_x_max: float | None = None,
    prefer_percent: bool = False,
) -> OcrBlock | None:
    if not blocks:
        return None
    filtered = []
    for block in blocks:
        if prefer_x_min is not None and block.x0 < prefer_x_min:
            continue
        if prefer_x_max is not None and block.x0 > prefer_x_max:
            continue
        if prefer_percent and "%" not in block.text:
            continue
        if not prefer_percent and "%" in block.text:
            continue
        filtered.append(block)
    if not filtered:
        return None
    filtered.sort(key=lambda block: (block.y0, block.x0))
    return filtered[0]


def looks_like_name(text: str) -> bool:
    if text in EXACT_NOISE or any(token in text for token in NOISE_SUBSTRINGS):
        return False
    if len(text) <= 1 or is_number_like(text) or re.fullmatch(r"[\W_]+", text):
        return False
    return any("\u4e00" <= ch <= "\u9fff" for ch in text) or any(
        ch.isalpha() for ch in text
    )


def is_number_like(text: str) -> bool:
    cleaned = clean_number(text).replace("-", "")
    return bool(re.fullmatch(r"\d+(\.\d+)?", cleaned))


def clean_number(text: str) -> str:
    return (
        text.replace(",", "")
        .replace("，", "")
        .replace("%", "")
        .replace("+", "")
        .replace("￥", "")
        .replace("¥", "")
        .replace("元", "")
        .replace(" ", "")
        .strip()
    )


def is_noise(text: str) -> bool:
    return text in EXACT_NOISE or any(token in text for token in NOISE_SUBSTRINGS)


def normalize_text(text: str) -> str:
    return text.replace("　", " ").strip()


if __name__ == "__main__":
    raise SystemExit(main())
