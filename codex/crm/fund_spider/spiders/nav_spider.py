from __future__ import annotations

import json
import logging
import math
import random
import re
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass
from typing import Any
from urllib.parse import urlencode

import requests

from db import FundNavHistory
from spiders.fund_ranking_spider import RequestConfig


logger = logging.getLogger(__name__)


NAV_API_URL = "https://api.fund.eastmoney.com/f10/lsjz"


@dataclass(frozen=True)
class NavPage:
    fund_code: str
    page_index: int
    page_size: int
    total_count: int
    total_pages: int
    rows: list[FundNavHistory]


class EastMoneyNavSpider:
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
                "Accept": "application/json, text/javascript, */*; q=0.01",
                "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
                "Connection": "keep-alive",
                "Referer": "https://fundf10.eastmoney.com/jjjz_519674.html",
                "Origin": "https://fundf10.eastmoney.com",
            }
        )

    def iter_pages(
        self,
        fund_code: str,
        page_size: int = 20,
        start_page: int = 1,
        max_pages: int | None = None,
        start_date: str = "",
        end_date: str = "",
        page_workers: int = 1,
    ):
        if page_workers < 1:
            raise ValueError("page_workers must be greater than or equal to 1")

        first_page = self.fetch_page(fund_code, start_page, page_size, start_date, end_date)
        yield first_page

        last_page = first_page.total_pages
        if max_pages is not None:
            last_page = min(last_page, start_page + max_pages - 1)

        page_indexes = range(start_page + 1, last_page + 1)
        if page_workers == 1:
            for page_index in page_indexes:
                yield self.fetch_page(fund_code, page_index, page_size, start_date, end_date)
            return

        worker_state = threading.local()

        def fetch_page(page_index: int) -> NavPage:
            worker_spider = getattr(worker_state, "spider", None)
            if worker_spider is None:
                worker_spider = EastMoneyNavSpider(self.config)
                worker_state.spider = worker_spider
            return worker_spider.fetch_page(
                fund_code,
                page_index,
                page_size,
                start_date,
                end_date,
            )

        worker_count = min(page_workers, max(0, last_page - start_page))
        if worker_count == 0:
            return
        logger.info(
            "step=nav_fetch fund=%s pages=%s-%s workers=%s",
            fund_code,
            start_page + 1,
            last_page,
            worker_count,
        )
        with ThreadPoolExecutor(
            max_workers=worker_count,
            thread_name_prefix="nav-page",
        ) as executor:
            yield from executor.map(fetch_page, page_indexes)

    def fetch_page(
        self,
        fund_code: str,
        page_index: int = 1,
        page_size: int = 20,
        start_date: str = "",
        end_date: str = "",
    ) -> NavPage:
        self.session.headers["Referer"] = f"https://fundf10.eastmoney.com/jjjz_{fund_code}.html"
        payload = self._get_json(build_nav_url(fund_code, page_index, page_size, start_date, end_date))
        if payload.get("ErrCode") != 0:
            raise RuntimeError(f"nav api returned ErrCode={payload.get('ErrCode')}: {payload.get('ErrMsg')}")

        total_count = int(payload.get("TotalCount") or 0)
        actual_page_size = int(payload.get("PageSize") or page_size)
        actual_page_index = int(payload.get("PageIndex") or page_index)
        total_pages = math.ceil(total_count / actual_page_size) if actual_page_size else actual_page_index
        data = payload.get("Data") or {}
        rows = parse_nav_rows(fund_code, data.get("LSJZList") or [])

        return NavPage(
            fund_code=fund_code,
            page_index=actual_page_index,
            page_size=actual_page_size,
            total_count=total_count,
            total_pages=total_pages,
            rows=rows,
        )

    def _get_json(self, url: str) -> dict[str, Any]:
        last_error: Exception | None = None
        for attempt in range(1, self.config.max_retries + 1):
            self._polite_delay(attempt)
            try:
                response = self.session.get(url, timeout=self.config.timeout_seconds)
                response.raise_for_status()
                text = response.text.strip()
                if not text:
                    raise RuntimeError("empty response body")
                return parse_json_or_jsonp(text)
            except (requests.RequestException, RuntimeError, json.JSONDecodeError) as exc:
                last_error = exc
                logger.warning("nav request failed, attempt %s/%s: %s", attempt, self.config.max_retries, exc)

            if attempt < self.config.max_retries:
                backoff = 2 ** attempt
                logger.info("waiting %s seconds before retry", backoff)
                time.sleep(backoff)

        raise RuntimeError(f"failed to fetch nav page after {self.config.max_retries} attempts") from last_error

    def _polite_delay(self, attempt: int) -> None:
        delay = random.uniform(self.config.min_delay_seconds, self.config.max_delay_seconds)
        if attempt > 1:
            delay += attempt
        logger.info("sleeping %.2f seconds before nav request", delay)
        time.sleep(delay)


def build_nav_url(
    fund_code: str,
    page_index: int = 1,
    page_size: int = 20,
    start_date: str = "",
    end_date: str = "",
) -> str:
    if page_index < 1:
        raise ValueError("page_index must be greater than or equal to 1")
    if page_size < 1:
        raise ValueError("page_size must be greater than or equal to 1")
    params = {
        "fundCode": fund_code,
        "pageIndex": str(page_index),
        "pageSize": str(page_size),
        "startDate": start_date,
        "endDate": end_date,
    }
    return f"{NAV_API_URL}?{urlencode(params)}"


def parse_json_or_jsonp(text: str) -> dict[str, Any]:
    if text.startswith("{"):
        return json.loads(text)

    match = re.match(r"^[^(]+\(([\s\S]+)\)\s*;?$", text)
    if not match:
        raise json.JSONDecodeError("response is not JSON or JSONP", text, 0)
    return json.loads(match.group(1))


def parse_nav_rows(fund_code: str, rows: list[dict[str, Any]]) -> list[FundNavHistory]:
    parsed: list[FundNavHistory] = []
    for row in rows:
        nav_date = parse_date(row.get("FSRQ"))
        if nav_date is None:
            continue

        parsed.append(
            FundNavHistory(
                fund_code=fund_code,
                nav_date=nav_date,
                unit_nav=clean_decimal(row.get("DWJZ")),
                accumulated_nav=clean_decimal(row.get("LJJZ")),
                daily_growth_rate=clean_decimal(row.get("JZZZL")),
            )
        )
    return parsed


def parse_date(value: Any) -> str | None:
    text = clean_text(value)
    if not text:
        return None
    return text.replace("-", "")


def clean_decimal(value: Any) -> str | None:
    text = clean_text(value)
    if not text or text == "--":
        return None
    text = text.replace("%", "").replace(",", "")
    return text if re.fullmatch(r"-?\d+(?:\.\d+)?", text) else None


def clean_text(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    return text or None
