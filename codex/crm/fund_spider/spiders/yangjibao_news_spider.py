from __future__ import annotations

import json
import logging
import os
import random
import time
from typing import Any

import requests

from db import YangjibaoNews
from spiders.fund_ranking_spider import RequestConfig


logger = logging.getLogger(__name__)
NEWS_URL = "https://app-api.yangjibao.com/news"
DEFAULT_USER_AGENT = "YJB/2.0.6 (com.xiaoduotou.yjb; build:911; iOS) Alamofire/5.11.2"


class YangjibaoNewsSpider:
    def __init__(self, config: RequestConfig) -> None:
        self.config = config
        authorization = os.getenv("YJB_AUTHORIZATION", "").strip()
        request_sign = os.getenv("YJB_REQUEST_SIGN", "").strip()
        request_time = os.getenv("YJB_REQUEST_TIME", "").strip()
        if not authorization or not request_sign or not request_time:
            raise ValueError("YJB_AUTHORIZATION, YJB_REQUEST_SIGN and YJB_REQUEST_TIME are required")
        self.session = requests.Session()
        self.session.headers.update({
            "Accept": "*/*",
            "Content-Type": "application/json",
            "Authorization": authorization,
            "Request-Sign": request_sign,
            "Request-Time": request_time,
            "Accept-Language": "zh-Hans-CN;q=1.0",
            "User-Agent": os.getenv("YJB_USER_AGENT", DEFAULT_USER_AGENT),
        })
        cookie = os.getenv("YJB_COOKIE", "").strip()
        if cookie:
            self.session.headers["Cookie"] = cookie

    def fetch_news(self, score: int = 2) -> list[YangjibaoNews]:
        payload = self._get_json(NEWS_URL, {"score": score})
        if int(payload.get("code") or 0) != 200:
            raise RuntimeError(f"Yangjibao API error: {payload.get('message') or payload.get('code')}")
        data = payload.get("data")
        if not isinstance(data, list):
            raise ValueError("Yangjibao news response data is not a list")
        return parse_news_rows(data)

    def _get_json(self, url: str, params: dict[str, Any]) -> dict[str, Any]:
        last_error: Exception | None = None
        for attempt in range(1, self.config.max_retries + 1):
            time.sleep(random.uniform(self.config.min_delay_seconds, self.config.max_delay_seconds))
            try:
                response = self.session.get(url, params=params, timeout=self.config.timeout_seconds)
                response.raise_for_status()
                payload = response.json()
                if not isinstance(payload, dict):
                    raise ValueError("response is not a JSON object")
                return payload
            except (requests.RequestException, ValueError) as exc:
                last_error = exc
                logger.warning("Yangjibao news request failed, attempt %s/%s: %s", attempt, self.config.max_retries, exc)
                if attempt < self.config.max_retries:
                    time.sleep(2 ** attempt)
        raise RuntimeError("failed to fetch Yangjibao news") from last_error


def parse_news_rows(rows: list[Any]) -> list[YangjibaoNews]:
    result: list[YangjibaoNews] = []
    for value in rows:
        if not isinstance(value, dict):
            continue
        news_id = str(value.get("_id") or "").strip()
        display_time = str(value.get("display_time") or "").strip()
        content = str(value.get("content") or "").strip()
        if not news_id or not display_time or not content:
            logger.warning("skip incomplete Yangjibao news row id=%s", news_id or "-")
            continue
        images = value.get("images") if isinstance(value.get("images"), list) else []
        result.append(YangjibaoNews(
            news_id=news_id,
            title=str(value.get("title") or "").strip() or None,
            content=content,
            display_time=display_time,
            images_json=json.dumps(images, ensure_ascii=False, separators=(",", ":")),
            score=_optional_int(value.get("score")),
            news_type=_optional_int(value.get("type")),
            source_json=json.dumps(value, ensure_ascii=False, separators=(",", ":")),
        ))
    return result


def _optional_int(value: Any) -> int | None:
    try:
        return int(value) if value is not None and str(value).strip() else None
    except (TypeError, ValueError):
        return None
