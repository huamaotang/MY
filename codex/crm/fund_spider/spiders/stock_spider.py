from __future__ import annotations

import json
import logging
import math
import random
import time
from dataclasses import dataclass
from datetime import datetime
from typing import Any
from zoneinfo import ZoneInfo

import requests

from spiders.fund_ranking_spider import RequestConfig


logger = logging.getLogger(__name__)


API_URLS = (
    "https://push2delay.eastmoney.com/api/qt/clist/get",
    "https://push2.eastmoney.com/api/qt/clist/get",
)
UT = "fa5fd1943c7b386f172d6893dbfba10b"
CN_STOCKS_FS = "m:0+t:6+f:!2,m:0+t:80+f:!2,m:1+t:2+f:!2,m:1+t:23+f:!2,m:0+t:81+s:262144+f:!2"
HK_STOCKS_FS = "m:116+t:3,m:116+t:4,m:116+t:1,m:116+t:2"
HK_DELAY_STOCKS_FS = "m:128+t:3,m:128+t:4,m:128+t:1,m:128+t:2"
MARKET_FILTERS = {
    "cn": (CN_STOCKS_FS,),
    "hk": (HK_STOCKS_FS, HK_DELAY_STOCKS_FS),
}
FIELDS = "f1,f2,f3,f4,f5,f6,f7,f8,f9,f10,f11,f12,f13,f14,f15,f16,f17,f18,f20,f21,f22,f23,f24,f25,f26,f62,f115,f124,f128,f136,f152"
MAX_PAGE_SIZE = 100
STABLE_SORT_FIELD = "f12"
SHANGHAI = ZoneInfo("Asia/Shanghai")


@dataclass(frozen=True)
class StockQuote:
    stock_code: str
    stock_name: str
    market_code: int
    exchange_name: str
    trade_date: str
    quote_time: str | None
    latest_price: str | None
    change_rate: str | None
    change_amount: str | None
    volume: int | None
    amount: str | None
    amplitude: str | None
    turnover_rate: str | None
    pe_dynamic: str | None
    volume_ratio: str | None
    five_min_change_rate: str | None
    high_price: str | None
    low_price: str | None
    open_price: str | None
    previous_close: str | None
    total_market_cap: str | None
    float_market_cap: str | None
    speed_rate: str | None
    pb_ratio: str | None
    change_rate_60d: str | None
    change_rate_ytd: str | None
    listing_date: str | None
    main_net_inflow: str | None
    pe_ttm: str | None
    raw_json: str


class EastMoneyStockSpider:
    def __init__(self, config: RequestConfig) -> None:
        self.config = config
        self.session = requests.Session()
        self.session.trust_env = False
        self.api_url = API_URLS[0]
        self.session.headers.update({
            "User-Agent": (
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                "AppleWebKit/537.36 (KHTML, like Gecko) "
                "Chrome/126.0.0.0 Safari/537.36"
            ),
            "Accept": "application/json,text/plain,*/*",
            "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
            "Connection": "close",
            "Referer": "https://quote.eastmoney.com/",
        })

    def fetch_all(self, page_size: int = MAX_PAGE_SIZE, market: str = "cn") -> list[StockQuote]:
        market = market.strip().lower()
        if market == "all":
            combined: dict[str, StockQuote] = {}
            for market_name in MARKET_FILTERS:
                for row in self.fetch_all(page_size=page_size, market=market_name):
                    combined[row.stock_code] = row
            return sorted(combined.values(), key=lambda row: row.stock_code)
        if market not in MARKET_FILTERS:
            raise ValueError(f"unsupported stock market: {market}")

        requested_page_size = max(1, int(page_size))
        effective_page_size = min(requested_page_size, MAX_PAGE_SIZE)
        if requested_page_size != effective_page_size:
            logger.info(
                "stock page size capped from %s to EastMoney limit %s",
                requested_page_size,
                effective_page_size,
            )

        last_error: RuntimeError | None = None
        market_filters = MARKET_FILTERS[market]
        for index, market_filter in enumerate(market_filters):
            try:
                return self._fetch_market(market, market_filter, effective_page_size)
            except RuntimeError as exc:
                last_error = exc
                if index + 1 < len(market_filters):
                    logger.warning(
                        "%s real-time stock crawl failed; retrying with delayed market data: %s",
                        market,
                        exc,
                    )
        raise RuntimeError(f"failed to fetch complete {market} stock market") from last_error

    def _fetch_market(
        self,
        market: str,
        market_filter: str,
        page_size: int,
    ) -> list[StockQuote]:
        unique: dict[str, StockQuote] = {}
        page = 1
        total: int | None = None
        total_pages: int | None = None
        while total_pages is None or page <= total_pages:
            payload = self._fetch_page(page, page_size, market_filter)
            data = payload.get("data") or {}
            response_total = int(data.get("total") or 0)
            values = data.get("diff") or []
            if total is None:
                if response_total <= 0 or not values:
                    raise RuntimeError("EastMoney stock response did not contain any quotes")
                total = response_total
                actual_page_size = len(values)
                total_pages = math.ceil(total / actual_page_size)
                logger.info(
                    "%s stock crawl started, total=%s page_size=%s pages=%s",
                    market,
                    total,
                    actual_page_size,
                    total_pages,
                )
            elif response_total > total:
                total = response_total
                total_pages = max(total_pages or 0, math.ceil(total / page_size))

            for row in parse_stock_quotes(values):
                unique[row.stock_code] = row
            logger.info(
                "%s stock page fetched, page=%s/%s rows=%s unique=%s",
                market,
                page,
                total_pages,
                len(values),
                len(unique),
            )
            if page >= (total_pages or 0):
                break
            page += 1
            self._polite_delay()

        if total is not None and len(unique) < total:
            raise RuntimeError(
                f"incomplete {market} stock response: expected={total}, parsed={len(unique)}"
            )
        return sorted(unique.values(), key=lambda row: row.stock_code)

    def _fetch_page(
        self,
        page: int,
        page_size: int,
        market_filter: str = CN_STOCKS_FS,
    ) -> dict[str, Any]:
        params = {
            "pn": page, "pz": page_size, "po": 1, "np": 1, "ut": UT, "fltt": 2,
            "invt": 2, "fid": STABLE_SORT_FIELD, "fs": market_filter, "fields": FIELDS,
            "_": int(time.time() * 1000),
        }
        last_error: Exception | None = None
        attempts = max(1, self.config.max_retries + 1)
        for attempt in range(1, attempts + 1):
            candidates = (self.api_url,) + tuple(url for url in API_URLS if url != self.api_url)
            for api_url in candidates:
                try:
                    response = self.session.get(api_url, params=params, timeout=self.config.timeout_seconds)
                    response.raise_for_status()
                    payload = response.json()
                    if not isinstance(payload, dict):
                        raise RuntimeError("EastMoney stock API returned a non-object payload")
                    if payload.get("rc") != 0 or not payload.get("data"):
                        raise RuntimeError(
                            "EastMoney stock API error: "
                            f"rc={payload.get('rc')} message={payload.get('message')}"
                        )
                    self.api_url = api_url
                    return payload
                except Exception as exc:
                    last_error = exc
                    logger.warning(
                        "stock page request failed, page=%s endpoint=%s attempt=%s/%s: %s",
                        page,
                        api_url,
                        attempt,
                        attempts,
                        exc,
                    )
            if attempt < attempts:
                backoff = min(2 ** (attempt - 1), 8) + self._random_delay()
                logger.info(
                    "waiting %.2f seconds before retrying stock page %s",
                    backoff,
                    page,
                )
                time.sleep(backoff)
        raise RuntimeError(f"failed to fetch stock page {page} after {attempts} attempts") from last_error

    def _polite_delay(self) -> None:
        delay = self._random_delay()
        if delay > 0:
            logger.info("sleeping %.2f seconds before next stock page", delay)
            time.sleep(delay)

    def _random_delay(self) -> float:
        minimum = max(0.0, self.config.min_delay_seconds)
        maximum = max(minimum, self.config.max_delay_seconds)
        return random.uniform(minimum, maximum)


def parse_stock_quotes(values: list[Any]) -> list[StockQuote]:
    rows: list[StockQuote] = []
    now = datetime.now(SHANGHAI)
    for value in values:
        if not isinstance(value, dict) or not value.get("f12") or not value.get("f14"):
            continue
        quote_dt = _quote_datetime(value.get("f124"))
        trade_date = (quote_dt or now).date().isoformat()
        source_market_code = int(value.get("f13") or 0)
        market_code = 116 if source_market_code in {116, 128} else source_market_code
        if market_code == 116:
            exchange_name = "香港"
        elif market_code == 1:
            exchange_name = "上海"
        elif str(value["f12"]).startswith(("4", "8", "9")):
            exchange_name = "北京"
        else:
            exchange_name = "深圳"
        rows.append(StockQuote(
            stock_code=str(value["f12"]), stock_name=str(value["f14"]), market_code=market_code,
            exchange_name=exchange_name,
            trade_date=trade_date, quote_time=quote_dt.strftime("%Y-%m-%d %H:%M:%S") if quote_dt else None,
            latest_price=_decimal(value.get("f2")), change_rate=_decimal(value.get("f3")),
            change_amount=_decimal(value.get("f4")), volume=_integer(value.get("f5")),
            amount=_decimal(value.get("f6")), amplitude=_decimal(value.get("f7")),
            turnover_rate=_decimal(value.get("f8")), pe_dynamic=_decimal(value.get("f9")),
            volume_ratio=_decimal(value.get("f10")), five_min_change_rate=_decimal(value.get("f11")),
            high_price=_decimal(value.get("f15")), low_price=_decimal(value.get("f16")),
            open_price=_decimal(value.get("f17")), previous_close=_decimal(value.get("f18")),
            total_market_cap=_decimal(value.get("f20")), float_market_cap=_decimal(value.get("f21")),
            speed_rate=_decimal(value.get("f22")), pb_ratio=_decimal(value.get("f23")),
            change_rate_60d=_decimal(value.get("f24")), change_rate_ytd=_decimal(value.get("f25")),
            listing_date=_date8(value.get("f26")), main_net_inflow=_decimal(value.get("f62")),
            pe_ttm=_decimal(value.get("f115")),
            raw_json=json.dumps(value, ensure_ascii=False, separators=(",", ":")),
        ))
    return rows


def _decimal(value: Any) -> str | None:
    if value in (None, "", "-", "--"):
        return None
    return str(value)


def _integer(value: Any) -> int | None:
    try:
        return int(value) if value not in (None, "", "-", "--") else None
    except (TypeError, ValueError):
        return None


def _date8(value: Any) -> str | None:
    text = str(value or "")
    return f"{text[:4]}-{text[4:6]}-{text[6:]}" if len(text) == 8 and text.isdigit() else None


def _quote_datetime(value: Any) -> datetime | None:
    try:
        timestamp = int(value)
        return datetime.fromtimestamp(timestamp, SHANGHAI) if timestamp > 0 else None
    except (TypeError, ValueError, OSError):
        return None
