from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from yangjibao_news_spider import parse_news_rows


class YangjibaoNewsParserTest(unittest.TestCase):
    def test_parses_and_preserves_news_fields(self) -> None:
        rows = parse_news_rows([{
            "_id": "6a617130c2be82e3b266ec22", "title": "", "content": "市场资讯",
            "display_time": "2026-07-23 09:41:00", "images": ["https://example.com/a.png"],
            "score": 2, "type": 1,
        }])
        self.assertEqual(1, len(rows))
        self.assertEqual("6a617130c2be82e3b266ec22", rows[0].news_id)
        self.assertIsNone(rows[0].title)
        self.assertEqual(2, rows[0].score)
        self.assertIn("a.png", rows[0].images_json)

    def test_skips_incomplete_rows(self) -> None:
        self.assertEqual([], parse_news_rows([{"_id": "missing-content"}, None]))


if __name__ == "__main__":
    unittest.main()
