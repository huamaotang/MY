from __future__ import annotations

import json
import time
from dataclasses import dataclass
from datetime import datetime
from typing import Any
from zoneinfo import ZoneInfo

import requests

from spider import RequestConfig


API_URLS = (
    "https://push2.eastmoney.com/api/qt/clist/get",
    "https://push2delay.eastmoney.com/api/qt/clist/get",
)
UT = "fa5fd1943c7b386f172d6893dbfba10b"
FS = "m:0+t:6+f:!2,m:0+t:80+f:!2,m:1+t:2+f:!2,m:1+t:23+f:!2,m:0+t:81+s:262144+f:!2"
FIELDS = "f1,f2,f3,f4,f5,f6,f7,f8,f9,f10,f11,f12,f13,f14,f15,f16,f17,f18,f20,f21,f22,f23,f24,f25,f26,f62,f115,f124,f128,f136,f152"
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
        self.session.headers.update({"User-Agent": "Mozilla/5.0", "Referer": "https://quote.eastmoney.com/"})

    def fetch_all(self, page_size: int = 200) -> list[StockQuote]:
        unique: dict[str, StockQuote] = {}
        page = 1
        total = None
        while total is None or len(unique) < total:
            payload = self._fetch_page(page, page_size)
            data = payload.get("data") or {}
            total = int(data.get("total") or 0)
            values = data.get("diff") or []
            before = len(unique)
            for row in parse_stock_quotes(values):
                unique[row.stock_code] = row
            if not values or len(unique) == before:
                break
            page += 1
            time.sleep(self.config.min_delay_seconds)
        if total and len(unique) < total:
            raise RuntimeError(f"incomplete stock response: expected={total}, parsed={len(unique)}")
        return list(unique.values())

    def _fetch_page(self, page: int, page_size: int) -> dict[str, Any]:
        params = {
            "pn": page, "pz": page_size, "po": 1, "np": 1, "ut": UT, "fltt": 2,
            "invt": 2, "fid": "f3", "fs": FS, "fields": FIELDS,
        }
        last_error: Exception | None = None
        for attempt in range(self.config.max_retries + 1):
            candidates = (self.api_url,) + tuple(url for url in API_URLS if url != self.api_url)
            for api_url in candidates:
                try:
                    response = self.session.get(api_url, params=params, timeout=self.config.timeout_seconds)
                    response.raise_for_status()
                    payload = response.json()
                    if int(payload.get("rc") or 0) != 0 or not payload.get("data"):
                        raise RuntimeError(f"EastMoney stock API error: {payload.get('message')}")
                    self.api_url = api_url
                    return payload
                except Exception as exc:
                    last_error = exc
            if attempt < self.config.max_retries:
                time.sleep(min(2 ** attempt, 5))
        if last_error:
            raise last_error
        raise RuntimeError("stock request failed") from last_error


def parse_stock_quotes(values: list[Any]) -> list[StockQuote]:
    rows: list[StockQuote] = []
    now = datetime.now(SHANGHAI)
    for value in values:
        if not isinstance(value, dict) or not value.get("f12") or not value.get("f14"):
            continue
        quote_dt = _quote_datetime(value.get("f124"))
        trade_date = (quote_dt or now).date().isoformat()
        market_code = int(value.get("f13") or 0)
        rows.append(StockQuote(
            stock_code=str(value["f12"]), stock_name=str(value["f14"]), market_code=market_code,
            exchange_name="上海" if market_code == 1 else ("北京" if str(value["f12"]).startswith(("4", "8", "9")) else "深圳"),
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
