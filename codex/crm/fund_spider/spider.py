from __future__ import annotations

import json
import logging
import random
import re
import time
from dataclasses import dataclass
from typing import Any
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit

import requests


logger = logging.getLogger(__name__)


FUND_LIST_URL = (
    "https://fund.eastmoney.com/Data/Fund_JJJZ_Data.aspx"
    "?t=10&lx=1&letter=&gsid=&text=&sort=rzdf,desc&page=1,200"
    "&dt=1784182683483&atfc=&onlySale=0&isLatest=0&_=1784182683484"
)


@dataclass(frozen=True)
class RequestConfig:
    min_delay_seconds: float = 1.5
    max_delay_seconds: float = 4.0
    timeout_seconds: float = 10.0
    max_retries: int = 3


@dataclass(frozen=True)
class FundPage:
    page_index: int
    page_size: int
    total_records: int
    total_pages: int
    funds: list[tuple[str, str]]


class EastMoneyFundSpider:
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
                "Referer": "https://fund.eastmoney.com/data/fundranking.html",
            }
        )

    def fetch_fund_list(self, url: str = FUND_LIST_URL) -> list[tuple[str, str]]:
        text = self._get_text(url)
        return parse_funds(text)

    def fetch_page(self, page_index: int = 1, page_size: int = 200, url: str = FUND_LIST_URL) -> FundPage:
        page_url = build_page_url(url, page_index, page_size)
        text = self._get_text(page_url)
        funds = parse_funds(text)
        total_records = _extract_int_field(text, "record", len(funds))
        total_pages = _extract_int_field(text, "pages", page_index)
        current_page = _extract_int_field(text, "curpage", page_index)
        return FundPage(
            page_index=current_page,
            page_size=page_size,
            total_records=total_records,
            total_pages=total_pages,
            funds=funds,
        )

    def iter_pages(
        self,
        start_page: int = 1,
        page_size: int = 200,
        max_pages: int | None = None,
        url: str = FUND_LIST_URL,
    ):
        first_page = self.fetch_page(start_page, page_size, url)
        yield first_page

        last_page = first_page.total_pages
        if max_pages is not None:
            last_page = min(last_page, start_page + max_pages - 1)

        for page_index in range(start_page + 1, last_page + 1):
            yield self.fetch_page(page_index, page_size, url)

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
            except requests.RequestException as exc:
                last_error = exc
                logger.warning("request failed, attempt %s/%s: %s", attempt, self.config.max_retries, exc)
            except RuntimeError as exc:
                last_error = exc
                logger.warning("invalid response, attempt %s/%s: %s", attempt, self.config.max_retries, exc)

            if attempt < self.config.max_retries:
                backoff = 2 ** attempt
                logger.info("waiting %s seconds before retry", backoff)
                time.sleep(backoff)

        raise RuntimeError(f"failed to fetch fund list after {self.config.max_retries} attempts") from last_error

    def _polite_delay(self, attempt: int) -> None:
        delay = random.uniform(self.config.min_delay_seconds, self.config.max_delay_seconds)
        if attempt > 1:
            delay += attempt
        logger.info("sleeping %.2f seconds before request", delay)
        time.sleep(delay)


def parse_funds(text: str) -> list[tuple[str, str]]:
    data = _extract_rank_data(text)
    funds: list[tuple[str, str]] = []

    for row in data:
        if not isinstance(row, list) or len(row) < 2:
            continue
        code = str(row[0]).strip()
        name = str(row[1]).strip()
        if code and name:
            funds.append((code, name))

    return funds


def build_page_url(url: str, page_index: int, page_size: int) -> str:
    if page_index < 1:
        raise ValueError("page_index must be greater than or equal to 1")
    if page_size < 1:
        raise ValueError("page_size must be greater than or equal to 1")

    parts = urlsplit(url)
    params = dict(parse_qsl(parts.query, keep_blank_values=True))
    now_ms = str(int(time.time() * 1000))
    params["page"] = f"{page_index},{page_size}"
    params["dt"] = now_ms
    params["_"] = now_ms
    query = urlencode(params, safe=",")
    return urlunsplit((parts.scheme, parts.netloc, parts.path, query, parts.fragment))


def _extract_rank_data(text: str) -> list[Any]:
    # EastMoney returns JavaScript such as: var db={chars:[...],datas:[...],...}
    marker = "datas:"
    marker_index = text.find(marker)
    if marker_index < 0:
        raise ValueError("could not find rankData.datas in response")

    array_start = text.find("[", marker_index + len(marker))
    if array_start < 0:
        raise ValueError("could not find datas array in response")

    array_end = _find_matching_bracket(text, array_start)
    datas_literal = text[array_start : array_end + 1]
    try:
        return json.loads(datas_literal)
    except json.JSONDecodeError as exc:
        raise ValueError("rankData.datas is not valid JSON") from exc


def _extract_int_field(text: str, field: str, default: int) -> int:
    match = re.search(rf'{re.escape(field)}\s*:\s*"?(\d+)"?', text)
    if not match:
        return default
    return int(match.group(1))


def _find_matching_bracket(text: str, start: int) -> int:
    depth = 0
    in_string = False
    escaped = False

    for index in range(start, len(text)):
        char = text[index]

        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            continue

        if char == '"':
            in_string = True
        elif char == "[":
            depth += 1
        elif char == "]":
            depth -= 1
            if depth == 0:
                return index

    raise ValueError("datas array is not closed")
