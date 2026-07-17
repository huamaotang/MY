from __future__ import annotations

import html
import json
import logging
import random
import re
import time
from urllib.parse import urljoin, urlencode

import requests

from db import FundStockHolding
from nav_spider import clean_decimal
from spider import RequestConfig


logger = logging.getLogger(__name__)


HOLDING_API_URL = "https://fundf10.eastmoney.com/FundArchivesDatas.aspx"


class EastMoneyHoldingSpider:
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
                "Accept": "*/*",
                "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
                "Connection": "keep-alive",
                "Referer": "https://fundf10.eastmoney.com/",
            }
        )

    def fetch_holdings(
        self,
        fund_code: str,
        top_line: int = 10,
        year: str = "",
        month: str = "",
    ) -> list[FundStockHolding]:
        self.session.headers["Referer"] = f"https://fundf10.eastmoney.com/ccmx_{fund_code}.html"
        text = self._get_text(build_holding_url(fund_code, top_line=top_line, year=year, month=month))
        return parse_holding_rows(fund_code, text)

    def _get_text(self, url: str) -> str:
        last_error: Exception | None = None
        for attempt in range(1, self.config.max_retries + 1):
            self._polite_delay(attempt)
            try:
                response = self.session.get(url, timeout=self.config.timeout_seconds)
                response.raise_for_status()
                response.encoding = response.apparent_encoding or "utf-8"
                text = response.text.strip()
                if not text:
                    raise RuntimeError("empty response body")
                return text
            except (requests.RequestException, RuntimeError) as exc:
                last_error = exc
                logger.warning("holding request failed, attempt %s/%s: %s", attempt, self.config.max_retries, exc)

            if attempt < self.config.max_retries:
                backoff = 2 ** attempt
                logger.info("waiting %s seconds before retry", backoff)
                time.sleep(backoff)

        raise RuntimeError(f"failed to fetch holding data after {self.config.max_retries} attempts") from last_error

    def _polite_delay(self, attempt: int) -> None:
        delay = random.uniform(self.config.min_delay_seconds, self.config.max_delay_seconds)
        if attempt > 1:
            delay += attempt
        logger.info("sleeping %.2f seconds before holding request", delay)
        time.sleep(delay)


def build_holding_url(fund_code: str, top_line: int = 10, year: str = "", month: str = "") -> str:
    params = {
        "type": "jjcc",
        "code": fund_code,
        "topline": str(top_line),
        "year": year,
        "month": month,
        "rt": f"{random.random():.17f}",
    }
    return f"{HOLDING_API_URL}?{urlencode(params)}"


def parse_holding_rows(fund_code: str, text: str) -> list[FundStockHolding]:
    content = _extract_content(text)
    groups = _split_report_tables(content)
    rows: list[FundStockHolding] = []

    for report_period, table_html in groups:
        report_date = _extract_report_date(report_period, table_html)
        if not report_date:
            logger.warning("skip holding table without report date, fund=%s period=%s", fund_code, report_period)
            continue
        normalized_report_period = _extract_report_period(report_period) or report_period[:50]

        for row_html in re.findall(r"<tr[^>]*>([\s\S]*?)</tr>", table_html, re.I):
            cells = [_html_to_text(cell) for cell in re.findall(r"<td[^>]*>([\s\S]*?)</td>", row_html, re.I)]
            if len(cells) < 9 or not cells[0].isdigit():
                continue

            stock_code = cells[1].strip()
            if not stock_code or stock_code == "--":
                continue

            rows.append(
                FundStockHolding(
                    fund_code=fund_code,
                    report_period=normalized_report_period or None,
                    report_date=report_date,
                    rank_no=int(cells[0]) if cells[0].isdigit() else None,
                    stock_code=stock_code,
                    stock_name=cells[2] or None,
                    latest_price=clean_decimal(cells[3]),
                    change_rate=clean_decimal(cells[4]),
                    related_info_url=_extract_first_link(row_html),
                    net_value_ratio=clean_decimal(cells[6]),
                    holding_shares_10k=clean_decimal(cells[7]),
                    holding_market_value_10k=clean_decimal(cells[8]),
                )
            )

    return rows


def _extract_content(text: str) -> str:
    match = re.search(r"content\s*:\s*\"((?:\\.|[^\"\\])*)\"", text)
    if not match:
        raise ValueError("holding content field not found")
    return json.loads(f'"{match.group(1)}"')


def _split_report_tables(content: str) -> list[tuple[str, str]]:
    groups: list[tuple[str, str]] = []
    header_pattern = re.compile(r"<h\d[^>]*>[\s\S]*?</h\d>", re.I)
    table_pattern = re.compile(r"<table[\s\S]*?</table>", re.I)
    headers = list(header_pattern.finditer(content))

    for match in table_pattern.finditer(content):
        title = ""
        for header in reversed(headers):
            if header.end() <= match.start():
                title = _html_to_text(header.group(0))
                break
        table = match.group(0)
        groups.append((title, table))

    if not groups:
        table_match = re.search(r"<table[\s\S]*?</table>", content, re.I)
        if table_match:
            groups.append(("", table_match.group(0)))
    return groups


def _extract_report_date(report_period: str, table_html: str) -> str | None:
    text = f"{report_period} {_html_to_text(table_html)}"
    exact_date = re.search(r"(20\d{2})[-/年](\d{1,2})[-/月](\d{1,2})", text)
    if exact_date:
        return f"{exact_date.group(1)}{int(exact_date.group(2)):02d}{int(exact_date.group(3)):02d}"

    year_match = re.search(r"(20\d{2})", text)
    if not year_match:
        return None
    year = year_match.group(1)

    if "1季度" in text or "一季度" in text:
        return f"{year}0331"
    if "2季度" in text or "二季度" in text or "中报" in text:
        return f"{year}0630"
    if "3季度" in text or "三季度" in text:
        return f"{year}0930"
    if "4季度" in text or "四季度" in text or "年报" in text:
        return f"{year}1231"
    return None


def _extract_report_period(report_period: str) -> str | None:
    match = re.search(r"(20\d{2}年[^ ]*?(?:股票|债券|资产|行业)?投资明细)", report_period)
    if match:
        return match.group(1)

    match = re.search(r"(20\d{2}年[一二三四\d]+季度)", report_period)
    if match:
        return match.group(1)

    match = re.search(r"(20\d{2}年(?:中报|年报))", report_period)
    if match:
        return match.group(1)
    return None


def _extract_first_link(row_html: str) -> str | None:
    match = re.search(r"<a[^>]+href=[\"']([^\"']+)[\"']", row_html, re.I)
    if not match:
        return None
    return urljoin("https://fundf10.eastmoney.com/", html.unescape(match.group(1)))


def _html_to_text(fragment: str) -> str:
    without_tags = re.sub(r"<[^>]+>", " ", fragment)
    unescaped = html.unescape(without_tags).replace("\xa0", " ")
    return re.sub(r"\s+", " ", unescaped).strip()
