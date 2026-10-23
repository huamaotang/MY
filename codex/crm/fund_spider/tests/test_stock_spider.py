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
    SinaStockSpider,
    parse_sina_stock_quotes,
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

    def test_parse_sina_cn_quote(self):
        rows = parse_sina_stock_quotes([
            {
                "symbol": "sh600000",
                "code": "600000",
                "name": "浦发银行",
                "trade": "10.200",
                "changepercent": "-1.500",
                "pricechange": "-0.155",
                "volume": 123400,
                "amount": "1258680",
                "settlement": "10.355",
                "open": "10.300",
                "high": "10.350",
                "low": "10.150",
                "ticktime": "09:42:48",
                "per": "5.6",
                "pb": "0.7",
                "mktcap": "1200",
                "nmc": "900",
                "turnoverratio": "0.4",
            }
        ], market="cn")

        self.assertEqual("600000", rows[0].stock_code)
        self.assertEqual("上海", rows[0].exchange_name)
        self.assertEqual(1, rows[0].market_code)
        self.assertEqual("10.200", rows[0].latest_price)
        self.assertEqual("-1.500", rows[0].change_rate)
        self.assertEqual(1234, rows[0].volume)
        self.assertEqual("12000000", rows[0].total_market_cap)

    def test_parse_sina_hk_quote(self):
        rows = parse_sina_stock_quotes([
            {
                "symbol": "00700",
                "code": "00700",
                "name": "腾讯控股",
                "lasttrade": "600.000",
                "changepercent": "1.200",
                "pricechange": "7.100",
                "volume": 100000,
                "amount": "60000000",
                "prevclose": "592.900",
                "open": "595.000",
                "high": "602.000",
                "low": "594.000",
                "ticktime": "2026/10/23 09:30:26",
                "pe_ratio": "20.1",
            }
        ], market="hk")

        self.assertEqual("00700", rows[0].stock_code)
        self.assertEqual(116, rows[0].market_code)
        self.assertEqual("香港", rows[0].exchange_name)
        self.assertEqual("2026-10-23", rows[0].trade_date)
        self.assertEqual("600.000", rows[0].latest_price)

    @patch("spiders.stock_spider.time.sleep")
    @patch("spiders.stock_spider.random.uniform", return_value=0)
    def test_fetch_all_falls_back_to_sina_when_eastmoney_is_blocked(self, _random, sleep):
        spider = EastMoneyStockSpider(RequestConfig(
            min_delay_seconds=0,
            max_delay_seconds=0,
            timeout_seconds=1,
            max_retries=0,
        ))
        spider._fetch_market = Mock(side_effect=RuntimeError("403 blocked"))
        sina = Mock()
        sina.fetch_all.return_value = [parse_stock_quotes([quote(1)])[0]]
        with patch("spiders.stock_spider.SinaStockSpider", return_value=sina):
            rows = spider.fetch_all(market="cn")

        self.assertEqual(1, len(rows))
        sina.fetch_all.assert_called_once()

    @patch("spiders.stock_spider.time.sleep")
    @patch("spiders.stock_spider.random.uniform", return_value=0)
    def test_sina_fetch_all_pages_until_short_page(self, _random, sleep):
        spider = SinaStockSpider(RequestConfig(
            min_delay_seconds=0,
            max_delay_seconds=0,
            timeout_seconds=1,
            max_retries=0,
        ))
        spider._fetch_page = Mock(side_effect=[
            [
                {
                    "symbol": "sh600000",
                    "code": "600000",
                    "name": "浦发银行",
                    "trade": "10.200",
                },
                {
                    "symbol": "sz000001",
                    "code": "000001",
                    "name": "平安银行",
                    "trade": "11.000",
                },
            ],
            [
                {
                    "symbol": "sh600004",
                    "code": "600004",
                    "name": "白云机场",
                    "trade": "9.000",
                }
            ],
        ])

        rows = spider.fetch_all(page_size=2, market="cn")

        self.assertEqual(3, len(rows))
        self.assertEqual(2, spider._fetch_page.call_count)

    @patch("spiders.stock_spider.time.sleep")
    @patch("spiders.stock_spider.random.uniform", return_value=0)
    def test_sina_hk_fetch_all_uses_60_row_page_cap(self, _random, sleep):
        spider = SinaStockSpider(RequestConfig(
            min_delay_seconds=0,
            max_delay_seconds=0,
            timeout_seconds=1,
            max_retries=0,
        ))
        first_page = [
            {
                "symbol": f"{index:05d}",
                "code": f"{index:05d}",
                "name": f"港股{index}",
                "lasttrade": "10.000",
            }
            for index in range(60)
        ]
        second_page = [
            {
                "symbol": "00700",
                "code": "00700",
                "name": "腾讯控股",
                "lasttrade": "600.000",
            }
        ]
        spider._fetch_page = Mock(side_effect=[first_page, second_page])

        rows = spider.fetch_all(page_size=100, market="hk")

        self.assertEqual(61, len(rows))
        self.assertEqual(2, spider._fetch_page.call_count)
        self.assertEqual(60, spider._fetch_page.call_args_list[0].args[1])
        self.assertEqual(60, spider._fetch_page.call_args_list[1].args[1])

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
