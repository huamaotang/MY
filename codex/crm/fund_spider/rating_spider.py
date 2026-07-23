from __future__ import annotations

import json
import logging
import random
import re
import time
from typing import Any
from urllib.parse import urlencode

import requests

from db import FundRating
from spider import RequestConfig


logger = logging.getLogger(__name__)


RATING_API_URL = "https://api.fund.eastmoney.com/F10/JJPJ/"
RATING_LIST_URL = "https://fund.eastmoney.com/data/fundrating.html"


class EastMoneyRatingSpider:
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
                "Accept": "application/json,text/javascript,*/*;q=0.8",
                "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
                "Connection": "keep-alive",
                "Referer": "https://fundf10.eastmoney.com/",
            }
        )

    def fetch_rating_list(self, url: str = RATING_LIST_URL) -> list[FundRating]:
        self.session.headers["Referer"] = RATING_LIST_URL
        return parse_rating_list(self._get_text(url))

    def fetch_ratings(
        self,
        fund_code: str,
        page_size: int = 50,
        max_pages: int | None = None,
    ) -> list[FundRating]:
        self.session.headers["Referer"] = f"https://fundf10.eastmoney.com/jjpj_{fund_code}.html"
        rows: list[FundRating] = []
        page_index = 1
        total_pages = 1

        while page_index <= total_pages:
            text = self._get_text(build_rating_url(fund_code, page_index=page_index, page_size=page_size))
            page = parse_rating_page(fund_code, text)
            rows.extend(page.rows)
            total_pages = max(1, (page.total_count + page.page_size - 1) // page.page_size)
            if max_pages is not None:
                total_pages = min(total_pages, max_pages)
            if page.page_size <= 0 or not page.rows:
                break
            page_index += 1

        return rows

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
                logger.warning("rating request failed, attempt %s/%s: %s", attempt, self.config.max_retries, exc)

            if attempt < self.config.max_retries:
                backoff = 2 ** attempt
                logger.info("waiting %s seconds before retry", backoff)
                time.sleep(backoff)

        raise RuntimeError(f"failed to fetch rating data after {self.config.max_retries} attempts") from last_error

    def _polite_delay(self, attempt: int) -> None:
        delay = random.uniform(self.config.min_delay_seconds, self.config.max_delay_seconds)
        if attempt > 1:
            delay += attempt
        logger.info("sleeping %.2f seconds before rating request", delay)
        time.sleep(delay)


class RatingPage:
    def __init__(self, rows: list[FundRating], total_count: int, page_size: int) -> None:
        self.rows = rows
        self.total_count = total_count
        self.page_size = page_size


def build_rating_url(fund_code: str, page_index: int = 1, page_size: int = 50) -> str:
    params = {
        "fundcode": fund_code,
        "pageIndex": str(page_index),
        "pageSize": str(page_size),
    }
    return f"{RATING_API_URL}?{urlencode(params)}"


def parse_rating_page(fund_code: str, text: str) -> RatingPage:
    payload = _loads_json_or_jsonp(text)
    err_code = int(payload.get("ErrCode") or 0)
    if err_code != 0:
        logger.warning(
            "rating api returned non-zero code, fund=%s err_code=%s err_msg=%s",
            fund_code,
            err_code,
            payload.get("ErrMsg"),
        )
        return RatingPage(rows=[], total_count=0, page_size=int(payload.get("PageSize") or 1))

    raw_rows = payload.get("Data") or []
    rows: list[FundRating] = []
    for raw in raw_rows:
        if not isinstance(raw, dict):
            continue
        rating_date = _clean_date(raw.get("RDATE"))
        if not rating_date:
            logger.warning("skip rating row without date, fund=%s raw=%r", fund_code, raw)
            continue
        rows.append(
            FundRating(
                fund_code=fund_code,
                rating_date=rating_date,
                zhaoshang_rating=_clean_rating(raw.get("ZSPJ")),
                shanghai_rating_3y=_clean_rating(raw.get("SZPJ3")),
                shanghai_rating_5y=_clean_rating(_first_present(raw, "SZPJ5", "ZSPJ5")),
                jian_rating=_clean_rating(raw.get("JAPJ")),
                morning_star_rating=_clean_rating(raw.get("CXPJ3")),
            )
        )

    return RatingPage(
        rows=rows,
        total_count=int(payload.get("TotalCount") or len(rows)),
        page_size=int(payload.get("PageSize") or len(rows) or 1),
    )


def parse_rating_list(text: str) -> list[FundRating]:
    data_match = re.search(r'var\s+fundinfos\s*=\s*"([\s\S]*?)"\s*;', text)
    if not data_match:
        raise ValueError("could not find fundinfos in rating list page")

    institution_dates = [
        _extract_list_rating_date(text, institution_id)
        for institution_id in (2, 3, 4, 5)
    ]
    rating_date = max((value for value in institution_dates if value), default=None)
    if not rating_date:
        raise ValueError("could not find institution rating date in rating list page")

    rows: list[FundRating] = []
    for raw_row in data_match.group(1).split("_"):
        fields = raw_row.split("|")
        if len(fields) < 18:
            logger.warning("skip malformed rating list row with %s fields", len(fields))
            continue
        fund_code = fields[0].strip()
        ratings = (
            _clean_rating(fields[10]),
            _clean_rating(fields[12]),
            _clean_rating(fields[16]),
            _clean_rating(fields[14]),
        )
        if not fund_code or not any(value is not None for value in ratings):
            continue
        rows.append(
            FundRating(
                fund_code=fund_code,
                rating_date=rating_date,
                zhaoshang_rating=ratings[0],
                shanghai_rating_3y=ratings[1],
                shanghai_rating_5y=None,
                jian_rating=ratings[2],
                morning_star_rating=ratings[3],
            )
        )
    return rows


def _extract_list_rating_date(text: str, institution_id: int) -> str | None:
    values = re.findall(rf"JG_{institution_id}_pjrq\s*=\s*\"([^\"]*)\"", text)
    dates = [_clean_date(value) for value in values]
    return max((value for value in dates if value), default=None)


def _loads_json_or_jsonp(text: str) -> dict[str, Any]:
    stripped = text.strip()
    if stripped.startswith("{"):
        return json.loads(stripped)
    match = re.search(r"^[^(]*\(([\s\S]*)\)\s*;?$", stripped)
    if match:
        return json.loads(match.group(1))
    raise ValueError("rating response is not json")


def _first_present(row: dict[str, Any], *keys: str) -> Any:
    for key in keys:
        value = row.get(key)
        if value is not None and str(value).strip():
            return value
    return None


def _clean_date(value: Any) -> str | None:
    if value is None:
        return None
    digits = re.sub(r"\D", "", str(value))
    if len(digits) < 8:
        return None
    return digits[:8]


def _clean_rating(value: Any) -> int | None:
    if value is None:
        return None
    text = str(value).strip()
    if not text or text == "--":
        return None
    try:
        rating = int(float(text))
    except ValueError:
        return None
    if rating <= 0:
        return None
    return rating
