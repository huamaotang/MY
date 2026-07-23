from __future__ import annotations

import json
from typing import Any

import requests

from db import SinaFinanceNews
from spider import RequestConfig


API_URL = "https://app.cj.sina.com.cn/api/news/pc"
SINA_NEWS_CATEGORIES = {
    0: "全部", 10: "A股", 1: "宏观", 110: "产业", 3: "公司", 4: "数据",
    5: "市场", 102: "国际", 6: "观点", 7: "央行", 8: "其他",
}


class SinaNewsSpider:
    def __init__(self, config: RequestConfig) -> None:
        self.config = config
        self.session = requests.Session()
        self.session.headers.update({"User-Agent": "Mozilla/5.0", "Referer": "https://finance.sina.com.cn/7x24/"})

    def fetch_news(self, page_size: int = 20, max_pages: int = 1, tag: int = 0) -> list[SinaFinanceNews]:
        rows: list[SinaFinanceNews] = []
        cursor_id: str | None = None
        for page in range(1, max_pages + 1):
            params: dict[str, Any] = {"page": 1, "size": page_size, "tag": tag, "type": 0}
            if cursor_id:
                params.update({"type": 1, "id": cursor_id})
            response = self.session.get(API_URL, params=params, timeout=self.config.timeout_seconds)
            response.raise_for_status()
            payload = response.json()
            status = payload.get("result", {}).get("status", {})
            if int(status.get("code") or 0) != 0:
                raise RuntimeError(f"Sina API error: {status.get('msg')}")
            feed = payload.get("result", {}).get("data", {}).get("feed", {})
            page_rows = parse_sina_news(feed.get("list") or [])
            rows.extend(page_rows)
            if not page_rows:
                break
            next_cursor = str(page_rows[-1].news_id)
            if next_cursor == cursor_id:
                break
            cursor_id = next_cursor
        return rows


def parse_sina_news(values: list[Any]) -> list[SinaFinanceNews]:
    rows: list[SinaFinanceNews] = []
    for value in values:
        if not isinstance(value, dict) or not value.get("id") or not value.get("rich_text") or not value.get("create_time"):
            continue
        multimedia = value.get("multimedia") if isinstance(value.get("multimedia"), dict) else {}
        images = multimedia.get("img_url") if isinstance(multimedia.get("img_url"), list) else []
        tags = value.get("tag") if isinstance(value.get("tag"), list) else []
        primary_tag = tags[0] if tags and isinstance(tags[0], dict) else {}
        category_tag = int(primary_tag.get("id") or 0)
        category_name = str(primary_tag.get("name") or SINA_NEWS_CATEGORIES.get(category_tag, "未分类"))
        rows.append(SinaFinanceNews(
            news_id=str(value["id"]), category_tag=category_tag, category_name=category_name,
            content=str(value["rich_text"]),
            create_time=str(value["create_time"]), update_time=str(value.get("update_time") or value["create_time"]),
            doc_url=str(value.get("docurl") or "") or None,
            tags_json=json.dumps(tags, ensure_ascii=False, separators=(",", ":")),
            images_json=json.dumps(images, ensure_ascii=False, separators=(",", ":")),
            source_json=json.dumps(value, ensure_ascii=False, separators=(",", ":")),
        ))
    return rows
