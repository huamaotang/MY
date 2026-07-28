from __future__ import annotations
import sys
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

import requests

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from spiders.fund_ranking_spider import RequestConfig
from spiders.stock_spider import (
    API_URLS,
    HK_DELAY_STOCKS_FS,
    HK_STOCKS_FS,
    EastMoneyStockSpider,
    parse_stock_quotes,
)


class FakeResponse:
    def __init__(self, payload):
        self.payload = payload

    def raise_for_status(self):
        return None

    def json(self):
        return self.payload


def quote(index: int) -> dict:
    return {
        "f12": f"{index:06d}",
        "f14": f"股票{index}",
        "f13": 1,
        "f2": 10,
        "f124": 1784770749,
    }


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

    def test_parse_hk_quote_normalizes_real_time_and_delayed_market_codes(self):
        rows = parse_stock_quotes([
            {"f12": "00700", "f14": "腾讯控股", "f13": 116, "f2": 600, "f124": 1785138819},
            {"f12": "09988", "f14": "阿里巴巴-W", "f13": 128, "f2": 150, "f124": 1785137918},
        ])

        self.assertEqual([116, 116], [row.market_code for row in rows])
        self.assertEqual(["香港", "香港"], [row.exchange_name for row in rows])

    @patch("spiders.stock_spider.time.sleep")
    @patch("spiders.stock_spider.random.uniform", return_value=0)
    def test_fetch_all_caps_page_size_and_uses_stable_sort(self, _random, sleep):
        spider = EastMoneyStockSpider(RequestConfig(
            min_delay_seconds=0,
            max_delay_seconds=0,
            timeout_seconds=1,
            max_retries=0,
        ))
        spider.session.get = Mock(side_effect=[
            FakeResponse({"rc": 0, "data": {"total": 150, "diff": [quote(i) for i in range(100)]}}),
            FakeResponse({"rc": 0, "data": {"total": 150, "diff": [quote(i) for i in range(100, 150)]}}),
        ])

        rows = spider.fetch_all(page_size=200)

        self.assertEqual(150, len(rows))
        self.assertEqual("000000", rows[0].stock_code)
        first_params = spider.session.get.call_args_list[0].kwargs["params"]
        self.assertEqual(100, first_params["pz"])
        self.assertEqual("f12", first_params["fid"])
        sleep.assert_not_called()

    @patch("spiders.stock_spider.time.sleep")
    def test_fetch_page_falls_back_to_second_endpoint(self, sleep):
        spider = EastMoneyStockSpider(RequestConfig(
            min_delay_seconds=0,
            max_delay_seconds=0,
            timeout_seconds=1,
            max_retries=0,
        ))
        spider.session.get = Mock(side_effect=[
            requests.ConnectionError("remote closed"),
            FakeResponse({"rc": 0, "data": {"total": 1, "diff": [quote(1)]}}),
        ])

        payload = spider._fetch_page(1, 100)

        self.assertEqual(1, payload["data"]["total"])
        self.assertEqual(API_URLS[1], spider.api_url)
        self.assertEqual(2, spider.session.get.call_count)
        sleep.assert_not_called()

    @patch("spiders.stock_spider.time.sleep")
    @patch("spiders.stock_spider.random.uniform", return_value=0)
    def test_fetch_page_retries_after_all_endpoints_fail(self, _random, sleep):
        spider = EastMoneyStockSpider(RequestConfig(
            min_delay_seconds=0,
            max_delay_seconds=0,
            timeout_seconds=1,
            max_retries=1,
        ))
        spider.session.get = Mock(side_effect=[
            requests.ConnectionError("delay endpoint closed"),
            requests.ConnectionError("primary endpoint closed"),
            FakeResponse({"rc": 0, "data": {"total": 1, "diff": [quote(1)]}}),
        ])

        payload = spider._fetch_page(1, 100)

        self.assertEqual(1, payload["data"]["total"])
        self.assertEqual(3, spider.session.get.call_count)
        sleep.assert_called_once_with(1)

    @patch("spiders.stock_spider.time.sleep")
    @patch("spiders.stock_spider.random.uniform", return_value=0)
    def test_hk_market_falls_back_to_delayed_filter(self, _random, sleep):
        spider = EastMoneyStockSpider(RequestConfig(
            min_delay_seconds=0,
            max_delay_seconds=0,
            timeout_seconds=1,
            max_retries=0,
        ))
        spider.session.get = Mock(side_effect=[
            requests.ConnectionError("real-time delay endpoint closed"),
            requests.ConnectionError("real-time primary endpoint closed"),
            FakeResponse({
                "rc": 0,
                "data": {
                    "total": 1,
                    "diff": [{
                        "f12": "00700",
                        "f14": "腾讯控股",
                        "f13": 128,
                        "f2": 600,
                        "f124": 1785137918,
                    }],
                },
            }),
        ])

        rows = spider.fetch_all(market="hk")

        self.assertEqual(1, len(rows))
        self.assertEqual(116, rows[0].market_code)
        calls = spider.session.get.call_args_list
        self.assertEqual(HK_STOCKS_FS, calls[0].kwargs["params"]["fs"])
        self.assertEqual(HK_DELAY_STOCKS_FS, calls[2].kwargs["params"]["fs"])
        sleep.assert_not_called()


if __name__ == "__main__":
    unittest.main()
