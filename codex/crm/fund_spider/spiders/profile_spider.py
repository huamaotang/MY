from __future__ import annotations

import html
import logging
import random
import re
import time

import requests

from db import FundProfile
from spiders.fund_ranking_spider import RequestConfig


logger = logging.getLogger(__name__)


DETAIL_URL_TEMPLATE = "https://fundf10.eastmoney.com/jjjz_{fund_code}.html"


class EastMoneyProfileSpider:
    def __init__(self, config: RequestConfig) -> None:
        self.config = config
        self.session = requests.Session()
        self.session.headers.update(
            {
                "User-Agent": (
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                    "AppleWebKit/537.36 (KHTML, like Gecko) "
                    "Chrome/126.0.0.0 Safari/537.36"
                ),
                "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
                "Connection": "keep-alive",
                "Referer": "https://fundf10.eastmoney.com/",
            }
        )

    def fetch_profile(self, fund_code: str) -> FundProfile:
        url = DETAIL_URL_TEMPLATE.format(fund_code=fund_code)
        text = self._get_text(url)
        return parse_profile(fund_code, text)

    def _get_text(self, url: str) -> str:
        last_error: Exception | None = None
        for attempt in range(1, self.config.max_retries + 1):
            self._polite_delay(attempt)
            try:
                response = self.session.get(url, timeout=self.config.timeout_seconds)
                response.raise_for_status()
                response.encoding = response.apparent_encoding or "utf-8"
                if not response.text.strip():
                    raise RuntimeError("empty response body")
                return response.text
            except (requests.RequestException, RuntimeError) as exc:
                last_error = exc
                logger.warning("profile request failed, attempt %s/%s: %s", attempt, self.config.max_retries, exc)

            if attempt < self.config.max_retries:
                backoff = 2 ** attempt
                logger.info("waiting %s seconds before retry", backoff)
                time.sleep(backoff)

        raise RuntimeError(f"failed to fetch profile after {self.config.max_retries} attempts") from last_error

    def _polite_delay(self, attempt: int) -> None:
        delay = random.uniform(self.config.min_delay_seconds, self.config.max_delay_seconds)
        if attempt > 1:
            delay += attempt
        logger.info("sleeping %.2f seconds before profile request", delay)
        time.sleep(delay)


def parse_profile(fund_code: str, text: str) -> FundProfile:
    block = _extract_profile_block(text)
    labels = [_html_to_text(match) for match in re.findall(r"<label[\s\S]*?</label>", block, re.I)]

    values: dict[str, str | None] = {
        "成立日期": None,
        "基金经理": None,
        "类型": None,
        "管理人": None,
        "净资产规模": None,
        "规模截止至日": None,
    }
    for label in labels:
        key, value = _split_label(label)
        if key in values:
            values[key] = value

    scale_text = values["净资产规模"]
    scale_date = _extract_date(scale_text)
    net_asset_scale = None
    if scale_text:
        net_asset_scale = re.split(r"[（(]\s*截止至[:：]", scale_text, maxsplit=1)[0].strip()

    return FundProfile(
        fund_code=fund_code,
        inception_date=_normalize_date(values["成立日期"]),
        fund_manager=values["基金经理"],
        fund_type=values["类型"],
        management_company=values["管理人"],
        net_asset_scale=net_asset_scale,
        scale_date=_normalize_date(scale_date),
    )


def _extract_profile_block(text: str) -> str:
    match = re.search(r'<div class="bs_gl">([\s\S]*?)</div>\s*</div>', text, re.I)
    if match:
        return match.group(1)
    if "成立日期" not in text:
        raise ValueError("profile block not found")
    start = max(0, text.find("成立日期") - 1000)
    end = min(len(text), text.find("历史净值", start) + 1000)
    return text[start:end]


def _html_to_text(fragment: str) -> str:
    without_tags = re.sub(r"<[^>]+>", "", fragment)
    unescaped = html.unescape(without_tags).replace("\xa0", " ")
    return re.sub(r"\s+", " ", unescaped).strip()


def _split_label(text: str) -> tuple[str, str | None]:
    if "：" in text:
        key, value = text.split("：", 1)
    elif ":" in text:
        key, value = text.split(":", 1)
    else:
        return text.strip(), None
    value = value.strip()
    return key.strip(), value or None


def _extract_date(text: str | None) -> str | None:
    if not text:
        return None
    match = re.search(r"\d{4}-\d{2}-\d{2}", text)
    return match.group(0) if match else None


def _normalize_date(text: str | None) -> str | None:
    if not text:
        return None
    match = re.search(r"\d{4}-\d{2}-\d{2}", text)
    return match.group(0) if match else None
