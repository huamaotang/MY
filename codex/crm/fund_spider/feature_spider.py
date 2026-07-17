from __future__ import annotations

import html
import logging
import random
import re
import time
from typing import Any

import requests

from db import FundFeatureData
from nav_spider import clean_decimal
from spider import RequestConfig


logger = logging.getLogger(__name__)


FEATURE_URL_TEMPLATE = "https://fundf10.eastmoney.com/tsdata_{fund_code}.html"


class EastMoneyFeatureSpider:
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

    def fetch_feature_data(self, fund_code: str) -> list[FundFeatureData]:
        url = FEATURE_URL_TEMPLATE.format(fund_code=fund_code)
        text = self._get_text(url)
        return parse_feature_data(fund_code, text)

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
                logger.warning("feature request failed, attempt %s/%s: %s", attempt, self.config.max_retries, exc)

            if attempt < self.config.max_retries:
                backoff = 2 ** attempt
                logger.info("waiting %s seconds before retry", backoff)
                time.sleep(backoff)

        raise RuntimeError(f"failed to fetch feature data after {self.config.max_retries} attempts") from last_error

    def _polite_delay(self, attempt: int) -> None:
        delay = random.uniform(self.config.min_delay_seconds, self.config.max_delay_seconds)
        if attempt > 1:
            delay += attempt
        logger.info("sleeping %.2f seconds before feature request", delay)
        time.sleep(delay)


def parse_feature_data(fund_code: str, text: str) -> list[FundFeatureData]:
    table = _extract_feature_table(text)
    period_labels = _extract_period_labels(table)
    row_values = _extract_metric_rows(table)
    cutoff_date = _extract_cutoff_date(text)

    standard_deviations = row_values.get("标准差", [])
    sharpe_ratios = row_values.get("夏普比率", [])
    rows: list[FundFeatureData] = []

    for index, period_label in enumerate(period_labels):
        rows.append(
            FundFeatureData(
                fund_code=fund_code,
                period_label=period_label,
                cutoff_date=cutoff_date,
                standard_deviation=_value_at(standard_deviations, index),
                sharpe_ratio=_value_at(sharpe_ratios, index),
            )
        )

    return rows


def _extract_feature_table(text: str) -> str:
    match = re.search(r'<table class="fxtb"[\s\S]*?</table>', text, re.I)
    if not match:
        raise ValueError("feature risk table not found")
    return match.group(0)


def _extract_period_labels(table: str) -> list[str]:
    headers = [_html_to_text(value) for value in re.findall(r"<th[^>]*>([\s\S]*?)</th>", table, re.I)]
    return [header for header in headers if header and header != "基金风险指标"]


def _extract_metric_rows(table: str) -> dict[str, list[str | None]]:
    rows: dict[str, list[str | None]] = {}
    for row_html in re.findall(r"<tr[^>]*>([\s\S]*?)</tr>", table, re.I):
        cells = [_html_to_text(value) for value in re.findall(r"<t[dh][^>]*>([\s\S]*?)</t[dh]>", row_html, re.I)]
        if len(cells) < 2:
            continue
        metric_name = cells[0]
        rows[metric_name] = [clean_decimal(value) for value in cells[1:]]
    return rows


def _extract_cutoff_date(text: str) -> str:
    match = re.search(r'<div class="limit-time"[^>]*>\s*截止至[:：]\s*(\d{4}-\d{2}-\d{2})\s*</div>', text, re.I)
    if not match:
        raise ValueError("feature cutoff date not found")
    return match.group(1).replace("-", "")


def _html_to_text(fragment: str) -> str:
    without_tags = re.sub(r"<[^>]+>", "", fragment)
    unescaped = html.unescape(without_tags).replace("\xa0", " ")
    return re.sub(r"\s+", " ", unescaped).strip()


def _value_at(values: list[Any], index: int) -> str | None:
    if index >= len(values):
        return None
    return values[index]
