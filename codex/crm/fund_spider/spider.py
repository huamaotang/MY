from __future__ import annotations

import json
import logging
import random
import re
import time
from dataclasses import dataclass
from datetime import date, datetime
from typing import Any
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit
from zoneinfo import ZoneInfo

import requests


logger = logging.getLogger(__name__)


FUND_LIST_URL = (
    "https://fund.eastmoney.com/data/rankhandler.aspx"
    "?op=ph&dt=kf&ft=all&rs=&gs=0&sc=1nzf&st=desc"
    "&sd=&ed=&qdii=&tabSubtype=,,,,,&pi=1&pn=50&dx=1&v=0"
)
PURCHASABLE_SALE_STATUSES = {"1", "2", "3", "8", "9"}


@dataclass(frozen=True)
class RequestConfig:
    min_delay_seconds: float = 1.5
    max_delay_seconds: float = 4.0
    timeout_seconds: float = 10.0
    max_retries: int = 3


@dataclass(frozen=True)
class FundRankingRow:
    fund_code: str
    fund_name: str
    fund_name_pinyin: str | None
    nav_date: str
    unit_nav: str | None
    accumulated_nav: str | None
    daily_growth_rate: str | None
    weekly_return_rate: str | None
    monthly_return_rate: str | None
    three_month_return_rate: str | None
    six_month_return_rate: str | None
    one_year_return_rate: str | None
    two_year_return_rate: str | None
    three_year_return_rate: str | None
    year_to_date_return_rate: str | None
    since_inception_return_rate: str | None
    inception_date: str | None
    sale_status: str | None
    custom_return_rate: str | None
    original_fee_rate: str | None
    discounted_fee_rate: str | None
    discount_factor: str | None
    cash_management_fee_rate: str | None
    custom_start_date: str
    custom_end_date: str
    can_buy: bool
    source_row: str


@dataclass(frozen=True)
class FundPage:
    page_index: int
    page_size: int
    total_records: int
    total_pages: int
    funds: list[FundRankingRow]


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

    def fetch_fund_list(self, url: str = FUND_LIST_URL) -> list[FundRankingRow]:
        start_date, end_date = rolling_one_year_window()
        text = self._get_text(build_page_url(url, 1, 50, start_date, end_date))
        return parse_funds(text, start_date, end_date)

    def fetch_page(
        self,
        page_index: int = 1,
        page_size: int = 50,
        url: str = FUND_LIST_URL,
        start_date: str | None = None,
        end_date: str | None = None,
    ) -> FundPage:
        if start_date is None or end_date is None:
            start_date, end_date = rolling_one_year_window()
        page_url = build_page_url(url, page_index, page_size, start_date, end_date)
        text = self._get_text(page_url)
        funds = parse_funds(text, start_date, end_date)
        total_records = _extract_int_field(text, "allRecords", len(funds))
        total_pages = _extract_int_field(text, "allPages", page_index)
        current_page = _extract_int_field(text, "pageIndex", page_index)
        response_page_size = _extract_int_field(text, "pageNum", page_size)
        return FundPage(
            page_index=current_page,
            page_size=response_page_size,
            total_records=total_records,
            total_pages=total_pages,
            funds=funds,
        )

    def iter_pages(
        self,
        start_page: int = 1,
        page_size: int = 50,
        max_pages: int | None = None,
        url: str = FUND_LIST_URL,
    ):
        start_date, end_date = rolling_one_year_window()
        first_page = self.fetch_page(start_page, page_size, url, start_date, end_date)
        yield first_page

        last_page = first_page.total_pages
        if max_pages is not None:
            last_page = min(last_page, start_page + max_pages - 1)

        for page_index in range(start_page + 1, last_page + 1):
            yield self.fetch_page(page_index, page_size, url, start_date, end_date)

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
                error_code = re.search(r"ErrCode\s*:\s*(-?\d+)", response.text)
                if error_code and int(error_code.group(1)) != 0:
                    raise RuntimeError("EastMoney ranking endpoint rejected the request")
                return response.text
            except requests.RequestException as exc:
                last_error = exc
                logger.warning("request failed, attempt %s/%s: %s", attempt, self.config.max_retries, exc)
            except RuntimeError as exc:
                last_error = exc
                logger.warning("invalid response, attempt %s/%s: %s", attempt, self.config.max_retries, exc)

            if attempt < self.config.max_retries:
                backoff = 2**attempt
                logger.info("waiting %s seconds before retry", backoff)
                time.sleep(backoff)

        raise RuntimeError(f"failed to fetch fund list after {self.config.max_retries} attempts") from last_error

    def _polite_delay(self, attempt: int) -> None:
        delay = random.uniform(self.config.min_delay_seconds, self.config.max_delay_seconds)
        if attempt > 1:
            delay += attempt
        logger.info("sleeping %.2f seconds before request", delay)
        time.sleep(delay)


def parse_funds(text: str, custom_start_date: str = "", custom_end_date: str = "") -> list[FundRankingRow]:
    data = _extract_rank_data(text)
    funds: list[FundRankingRow] = []

    for raw_row in data:
        if not isinstance(raw_row, str):
            raise ValueError("rankData.datas row must be a comma-separated string")
        fields = raw_row.split(",")
        if len(fields) < 23:
            raise ValueError(f"ranking row has {len(fields)} fields; expected at least 23")
        code = fields[0].strip()
        name = fields[1].strip()
        nav_date = _compact_date(fields[3])
        if not code or not name:
            raise ValueError("ranking row is missing fund code or name")
        sale_status = _optional_text(fields[17])
        funds.append(
            FundRankingRow(
                fund_code=code,
                fund_name=name,
                fund_name_pinyin=_optional_text(fields[2]),
                nav_date=nav_date,
                unit_nav=_optional_number(fields[4]),
                accumulated_nav=_optional_number(fields[5]),
                daily_growth_rate=_optional_percentage(fields[6]),
                weekly_return_rate=_optional_percentage(fields[7]),
                monthly_return_rate=_optional_percentage(fields[8]),
                three_month_return_rate=_optional_percentage(fields[9]),
                six_month_return_rate=_optional_percentage(fields[10]),
                one_year_return_rate=_optional_percentage(fields[11]),
                two_year_return_rate=_optional_percentage(fields[12]),
                three_year_return_rate=_optional_percentage(fields[13]),
                year_to_date_return_rate=_optional_percentage(fields[14]),
                since_inception_return_rate=_optional_percentage(fields[15]),
                inception_date=_optional_iso_date(fields[16]),
                sale_status=sale_status,
                custom_return_rate=_optional_percentage(fields[18]),
                original_fee_rate=_optional_percentage(fields[19]),
                discounted_fee_rate=_optional_percentage(fields[20]),
                discount_factor=_optional_number(fields[21]),
                cash_management_fee_rate=_optional_percentage(fields[22]),
                custom_start_date=custom_start_date,
                custom_end_date=custom_end_date,
                can_buy=sale_status in PURCHASABLE_SALE_STATUSES,
                source_row=raw_row,
            )
        )

    return funds


def rolling_one_year_window(reference_date: date | None = None) -> tuple[str, str]:
    end_date = reference_date or datetime.now(ZoneInfo("Asia/Shanghai")).date()
    try:
        start_date = end_date.replace(year=end_date.year - 1)
    except ValueError:
        start_date = end_date.replace(year=end_date.year - 1, day=28)
    return start_date.isoformat(), end_date.isoformat()


def build_page_url(
    url: str,
    page_index: int,
    page_size: int,
    start_date: str | None = None,
    end_date: str | None = None,
) -> str:
    if page_index < 1:
        raise ValueError("page_index must be greater than or equal to 1")
    if page_size < 1:
        raise ValueError("page_size must be greater than or equal to 1")
    if start_date is None or end_date is None:
        start_date, end_date = rolling_one_year_window()

    parts = urlsplit(url)
    params = dict(parse_qsl(parts.query, keep_blank_values=True))
    params["pi"] = str(page_index)
    params["pn"] = str(page_size)
    params["sd"] = start_date
    params["ed"] = end_date
    params["v"] = str(random.random())
    query = urlencode(params, safe=",|")
    return urlunsplit((parts.scheme, parts.netloc, parts.path, query, parts.fragment))


def _extract_rank_data(text: str) -> list[Any]:
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


def _optional_text(value: str) -> str | None:
    text = value.strip()
    return None if text in {"", "--", "---"} else text


def _optional_number(value: str) -> str | None:
    return _optional_text(value.replace("%", ""))


def _optional_percentage(value: str) -> str | None:
    return _optional_number(value)


def _compact_date(value: str) -> str:
    return value.strip().replace("-", "")


def _optional_iso_date(value: str) -> str | None:
    text = _optional_text(value)
    return text if text and re.fullmatch(r"\d{4}-\d{2}-\d{2}", text) else None


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
