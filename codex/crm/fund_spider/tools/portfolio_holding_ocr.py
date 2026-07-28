from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass, asdict
from datetime import date
from pathlib import Path
from statistics import mean
from typing import Any

try:
    from rapidocr_onnxruntime import RapidOCR
except Exception as exc:  # pragma: no cover
    raise SystemExit(
        "rapidocr-onnxruntime is required. Install it in the local Python environment."
    ) from exc


EXACT_NOISE = {
    "全部",
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
}

NOISE_SUBSTRINGS = {
    "更多产品，去市场看看>",
    "基金销售服务由蚂蚁（杭州）基金销售有限公司提供",
    "本页面非任何法律文件，收益数据仅供参考。过往业绩不预示未来表现，市场有风险，投资需谨慎。",
    "该页面由蚂蚁财富平台设计",
}


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
    args = parser.parse_args()

    screenshot_date = args.screenshot_date or date.today().isoformat()
    engine = RapidOCR()
    images = []
    warnings: list[str] = []

    for image_path in args.image:
        image_file = Path(image_path)
        if not image_file.exists():
            raise SystemExit(f"image not found: {image_path}")

        blocks = run_ocr(engine, image_path)
        rows = parse_rows(blocks, screenshot_date, warnings)
        images.append(
            {
                "imagePath": str(image_file),
                "rows": rows,
                "blocks": [asdict(block) for block in blocks],
            }
        )

    payload: dict[str, Any] = {
        "images": images,
        "warnings": warnings,
    }
    if args.json:
        json.dump(payload, sys.stdout, ensure_ascii=False)
    else:
        for image in images:
            print(image["imagePath"])
            for row in image["rows"]:
                print(row)
    return 0


def run_ocr(engine: RapidOCR, image_path: str) -> list[OcrBlock]:
    result, _ = engine(image_path)
    blocks: list[OcrBlock] = []
    for item in result or []:
        box, text, score = item
        text = normalize_text(str(text))
        if not text:
            continue
        x0 = min(point[0] for point in box)
        y0 = min(point[1] for point in box)
        x1 = max(point[0] for point in box)
        y1 = max(point[1] for point in box)
        blocks.append(OcrBlock(text=text, score=float(score), x0=x0, y0=y0, x1=x1, y1=y1))
    blocks.sort(key=lambda block: (block.y0, block.x0))
    return blocks


def parse_rows(blocks: list[OcrBlock], screenshot_date: str, warnings: list[str]) -> list[dict[str, Any]]:
    candidates = [block for block in blocks if block.y0 >= 600 and block.y1 <= 2500 and not is_noise(block.text)]
    groups = cluster_blocks(candidates)
    rows: list[dict[str, Any]] = []
    for group in groups:
        row = parse_group(group, screenshot_date)
        if row is None:
            continue
        rows.append(row)
    if not rows:
        warnings.append("no portfolio rows detected")
    return rows


def cluster_blocks(blocks: list[OcrBlock]) -> list[list[OcrBlock]]:
    if not blocks:
        return []
    groups: list[list[OcrBlock]] = []
    current = [blocks[0]]
    last_y = blocks[0].y0
    for block in blocks[1:]:
        if block.y0 - last_y > 150:
            groups.append(current)
            current = [block]
        else:
            current.append(block)
        last_y = block.y0
    groups.append(current)
    return groups


def parse_group(group: list[OcrBlock], screenshot_date: str) -> dict[str, Any] | None:
    if not group:
        return None
    left = [block for block in group if block.x0 < 620 and looks_like_name(block.text)]
    if not left:
        return None

    left = sorted(left, key=lambda block: (block.y0, block.x0))
    name_parts: list[str] = []
    for block in left:
        if name_parts and block.y0 - left[0].y0 > 120 and not re.search(r"[A-Za-z0-9]", block.text):
            continue
        name_parts.append(block.text)
    fund_name = "".join(name_parts).strip()
    if not fund_name:
        return None

    top_numbers = [block for block in group if block.y0 <= group[0].y0 + 70 and is_number_like(block.text)]
    bottom_numbers = [block for block in group if block.y0 > group[0].y0 + 40 and is_number_like(block.text)]

    amount = pick_number(top_numbers, prefer_x_min=620, prefer_x_max=980, prefer_percent=False)
    holding_profit = pick_number([block for block in top_numbers if block.x0 >= 950], prefer_percent=False)
    yesterday_profit = pick_number(bottom_numbers, prefer_x_min=620, prefer_x_max=980, prefer_percent=False)
    holding_return_rate = pick_number([block for block in bottom_numbers if "%" in block.text], prefer_percent=True)

    if amount is None and holding_profit is None and yesterday_profit is None and holding_return_rate is None:
        return None

    raw_texts = [block.text for block in group]
    confidences = [block.score for block in group if block.score > 0]
    return {
        "fundName": fund_name,
        "holdingAmount": amount.text if amount else None,
        "holdingProfit": holding_profit.text if holding_profit else None,
        "holdingReturnRate": holding_return_rate.text if holding_return_rate else None,
        "yesterdayProfit": yesterday_profit.text if yesterday_profit else None,
        "todayProfit": None,
        "holdingShares": None,
        "costNav": None,
        "screenshotDate": screenshot_date,
        "confidence": round(mean(confidences), 4) if confidences else None,
        "rawTexts": raw_texts,
    }


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
        filtered = blocks
    filtered.sort(key=lambda block: (block.y0, block.x0))
    return filtered[0]


def looks_like_name(text: str) -> bool:
    if text in EXACT_NOISE:
        return False
    if any(token in text for token in NOISE_SUBSTRINGS):
        return False
    if len(text) <= 1:
        return False
    if is_number_like(text):
        return False
    if re.fullmatch(r"[\W_]+", text):
        return False
    return any("\u4e00" <= ch <= "\u9fff" for ch in text) or any(ch.isalpha() for ch in text)


def is_number_like(text: str) -> bool:
    cleaned = text.replace(",", "").replace("%", "").replace("+", "").replace("-", "")
    return bool(re.fullmatch(r"\d+(\.\d+)?", cleaned))


def is_noise(text: str) -> bool:
    if text in EXACT_NOISE:
        return True
    for token in NOISE_SUBSTRINGS:
        if token in text:
            return True
    return False


def normalize_text(text: str) -> str:
    return text.replace("　", " ").strip()


if __name__ == "__main__":
    raise SystemExit(main())
