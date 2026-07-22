from __future__ import annotations

import sys
import unittest
from datetime import date
from pathlib import Path
from urllib.parse import parse_qs, urlsplit


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from db import upsert_fund_rankings  # noqa: E402
from spider import (  # noqa: E402
    EastMoneyFundSpider,
    FUND_LIST_URL,
    PURCHASABLE_SALE_STATUSES,
    RequestConfig,
    build_page_url,
    parse_funds,
    rolling_one_year_window,
)


SAMPLE_RESPONSE = """var rankData = {datas:[
"013840,银华集成电路混合A,YHJCDLHHA,2026-07-21,3.4468,3.4468,16.56,-5.49,13.74,89.6,72.23,240.39,334.65,269.27,120.86,244.68,2021-12-08,1,239.0517,1.50%,0.15%,1,0.15%,1,",
"013841,银华集成电路混合C,YHJCDLHHC,2026-07-21,3.415,3.415,16.56,-5.5,13.72,89.5,72.05,239.7,332.88,267.05,120.62,241.5,2021-12-08,0,238.3533,,0.00%,,,,"]
,allRecords:2,pageIndex:1,pageNum:2,allPages:1};"""


class FundRankingParserTest(unittest.TestCase):
    def test_parses_all_business_fields(self) -> None:
        rows = parse_funds(SAMPLE_RESPONSE, "2025-07-22", "2026-07-22")

        self.assertEqual(2, len(rows))
        first = rows[0]
        self.assertEqual("013840", first.fund_code)
        self.assertEqual("YHJCDLHHA", first.fund_name_pinyin)
        self.assertEqual("20260721", first.nav_date)
        self.assertEqual("3.4468", first.unit_nav)
        self.assertEqual("16.56", first.daily_growth_rate)
        self.assertEqual("240.39", first.one_year_return_rate)
        self.assertEqual("2021-12-08", first.inception_date)
        self.assertEqual("239.0517", first.custom_return_rate)
        self.assertEqual("1.50", first.original_fee_rate)
        self.assertEqual("0.15", first.discounted_fee_rate)
        self.assertEqual("1", first.discount_factor)
        self.assertEqual("0.15", first.cash_management_fee_rate)
        self.assertEqual("2025-07-22", first.custom_start_date)
        self.assertTrue(first.can_buy)

        second = rows[1]
        self.assertFalse(second.can_buy)
        self.assertIsNone(second.original_fee_rate)
        self.assertEqual("0.00", second.discounted_fee_rate)
        self.assertIsNone(second.cash_management_fee_rate)

    def test_all_purchase_statuses_match_page_behavior(self) -> None:
        for status in PURCHASABLE_SALE_STATUSES:
            response = SAMPLE_RESPONSE.replace(",1,239.0517,", f",{status},239.0517,")
            self.assertTrue(parse_funds(response)[0].can_buy)

    def test_rejects_shifted_or_incomplete_rows(self) -> None:
        with self.assertRaisesRegex(ValueError, "expected at least 23"):
            parse_funds('var rankData={datas:["000001,基金A"]};')

    def test_keeps_new_fund_without_nav_date(self) -> None:
        response = SAMPLE_RESPONSE.replace("2026-07-21,3.4468", ",3.4468", 1)
        row = parse_funds(response)[0]
        self.assertEqual("013840", row.fund_code)
        self.assertEqual("", row.nav_date)


class FundRankingRequestTest(unittest.TestCase):
    def test_builds_ranking_page_url(self) -> None:
        url = build_page_url(FUND_LIST_URL, 3, 50, "2025-07-22", "2026-07-22")
        params = parse_qs(urlsplit(url).query, keep_blank_values=True)

        self.assertEqual(["3"], params["pi"])
        self.assertEqual(["50"], params["pn"])
        self.assertEqual(["1nzf"], params["sc"])
        self.assertEqual(["desc"], params["st"])
        self.assertEqual(["2025-07-22"], params["sd"])
        self.assertEqual(["2026-07-22"], params["ed"])

    def test_rolling_window_handles_leap_day(self) -> None:
        self.assertEqual(
            ("2023-02-28", "2024-02-29"),
            rolling_one_year_window(date(2024, 2, 29)),
        )

    def test_reads_ranking_pagination_metadata(self) -> None:
        spider = EastMoneyFundSpider(RequestConfig(0, 0, 1, 1))
        spider._get_text = lambda _: SAMPLE_RESPONSE  # type: ignore[method-assign]

        page = spider.fetch_page(
            page_index=1,
            page_size=2,
            start_date="2025-07-22",
            end_date="2026-07-22",
        )

        self.assertEqual(1, page.page_index)
        self.assertEqual(2, page.page_size)
        self.assertEqual(2, page.total_records)
        self.assertEqual(1, page.total_pages)


class _FakeCursor:
    def __init__(self, fail_on_call: int | None = None) -> None:
        self.calls: list[tuple[str, list[tuple]]] = []
        self.fail_on_call = fail_on_call

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, traceback):
        return False

    def executemany(self, sql: str, values: list[tuple]) -> None:
        self.calls.append((sql, values))
        if self.fail_on_call == len(self.calls):
            raise RuntimeError("database failure")


class _FakeConnection:
    def __init__(self, fail_on_call: int | None = None) -> None:
        self.fake_cursor = _FakeCursor(fail_on_call)
        self.commits = 0
        self.rollbacks = 0

    def cursor(self) -> _FakeCursor:
        return self.fake_cursor

    def commit(self) -> None:
        self.commits += 1

    def rollback(self) -> None:
        self.rollbacks += 1


class FundRankingDatabaseTest(unittest.TestCase):
    def test_writes_three_tables_in_one_transaction(self) -> None:
        connection = _FakeConnection()
        rows = parse_funds(SAMPLE_RESPONSE, "2025-07-22", "2026-07-22")

        self.assertEqual(2, upsert_fund_rankings(connection, rows))
        self.assertEqual(3, len(connection.fake_cursor.calls))
        self.assertEqual(1, connection.commits)
        self.assertEqual(0, connection.rollbacks)

    def test_rolls_back_whole_page_on_failure(self) -> None:
        connection = _FakeConnection(fail_on_call=3)
        rows = parse_funds(SAMPLE_RESPONSE, "2025-07-22", "2026-07-22")

        with self.assertRaisesRegex(RuntimeError, "database failure"):
            upsert_fund_rankings(connection, rows)
        self.assertEqual(0, connection.commits)
        self.assertEqual(1, connection.rollbacks)


if __name__ == "__main__":
    unittest.main()
