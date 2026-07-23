from __future__ import annotations
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from stock_spider import parse_stock_quotes


class StockSpiderTest(unittest.TestCase):
    def test_parse_quote(self):
        rows = parse_stock_quotes([{
            "f12": "600000", "f14": "浦发银行", "f13": 1, "f2": 10.2, "f3": -1.5,
            "f5": 1234, "f124": 1784770749, "f26": 19991110,
        }])
        self.assertEqual("600000", rows[0].stock_code)
        self.assertEqual("上海", rows[0].exchange_name)
        self.assertEqual("-1.5", rows[0].change_rate)
        self.assertEqual("1999-11-10", rows[0].listing_date)

    def test_missing_values(self):
        row = parse_stock_quotes([{"f12": "000001", "f14": "平安银行", "f2": "-", "f5": "-"}])[0]
        self.assertIsNone(row.latest_price)
        self.assertIsNone(row.volume)


if __name__ == "__main__":
    unittest.main()
