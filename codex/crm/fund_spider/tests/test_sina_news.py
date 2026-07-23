from __future__ import annotations
import sys, unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from sina_news_spider import parse_sina_news

class SinaNewsParserTest(unittest.TestCase):
    def test_parse(self):
        rows = parse_sina_news([{"id": 1, "rich_text": "资讯", "create_time": "2026-07-23 10:00:00", "tag": [{"id":"5","name":"市场"}], "docurl":"https://example.com"}])
        self.assertEqual("1", rows[0].news_id)
        self.assertEqual(5, rows[0].category_tag)
        self.assertEqual("市场", rows[0].category_name)
        self.assertIn("市场", rows[0].tags_json)

if __name__ == "__main__": unittest.main()
