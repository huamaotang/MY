from __future__ import annotations

import os
import sys
import threading
import time
import unittest
from pathlib import Path
from unittest.mock import patch


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import cli  # noqa: E402
import jobs  # noqa: E402
from db import DatabaseConfig, FundNavHistory, log_write_sql  # noqa: E402
from spiders.fund_ranking_spider import RequestConfig  # noqa: E402
from spiders.nav_spider import EastMoneyNavSpider, NavPage  # noqa: E402


def nav_row(fund_code: str, nav_date: str) -> FundNavHistory:
    return FundNavHistory(fund_code, nav_date, "1.0", "1.0", "0.1")


def nav_page(page_index: int, rows: list[FundNavHistory], total_pages: int = 3) -> NavPage:
    return NavPage(
        fund_code="519674",
        page_index=page_index,
        page_size=20,
        total_count=total_pages * 20,
        total_pages=total_pages,
        rows=rows,
    )


class NavSpiderConcurrencyTest(unittest.TestCase):
    def test_fetches_remaining_pages_concurrently_and_yields_in_page_order(self) -> None:
        active = 0
        max_active = 0
        lock = threading.Lock()

        def fake_fetch(
            _spider,
            _fund_code,
            page_index=1,
            _page_size=20,
            _start_date="",
            _end_date="",
        ):
            nonlocal active, max_active
            if page_index == 1:
                return nav_page(1, [nav_row("519674", "20260731")], total_pages=4)
            with lock:
                active += 1
                max_active = max(max_active, active)
            time.sleep(0.02)
            with lock:
                active -= 1
            return nav_page(
                page_index,
                [nav_row("519674", f"202607{32 - page_index:02d}")],
                total_pages=4,
            )

        spider = EastMoneyNavSpider(RequestConfig(0, 0, 1, 1))
        with patch.object(EastMoneyNavSpider, "fetch_page", autospec=True, side_effect=fake_fetch):
            pages = list(spider.iter_pages("519674", page_workers=3))

        self.assertEqual([1, 2, 3, 4], [page.page_index for page in pages])
        self.assertGreater(max_active, 1)

    def test_rejects_invalid_worker_count(self) -> None:
        spider = EastMoneyNavSpider(RequestConfig(0, 0, 1, 1))
        with self.assertRaisesRegex(ValueError, "page_workers"):
            list(spider.iter_pages("519674", page_workers=0))


class _FakeConnection:
    def __init__(self) -> None:
        self.closed = False

    def close(self) -> None:
        self.closed = True


class _FakeSpider:
    def __init__(self) -> None:
        self.iter_kwargs = {}

    def iter_pages(self, **kwargs):
        self.iter_kwargs = kwargs
        yield nav_page(
            1,
            [
                nav_row("519674", "20260731"),
                nav_row("519674", "20260730"),
            ],
        )
        yield nav_page(
            2,
            [
                nav_row("519674", "20260729"),
                nav_row("519674", "20260728"),
            ],
        )
        yield nav_page(3, [nav_row("519674", "20260727")])


class NavHistoryJobTest(unittest.TestCase):
    def test_batches_multiple_pages_before_database_upsert(self) -> None:
        fake_connection = _FakeConnection()
        fake_spider = _FakeSpider()
        saved_batches: list[list[FundNavHistory]] = []
        options = jobs.NavHistoryOptions(
            selector=jobs.BatchSelector(fund_code="519674"),
            page_workers=3,
            write_batch_size=3,
        )

        def save_rows(_connection, rows):
            saved_batches.append(list(rows))
            return len(rows)

        with (
            patch.object(jobs, "ensure_schema"),
            patch.object(jobs, "load_selected_fund_codes", return_value=["519674"]),
            patch.object(jobs, "connect", return_value=fake_connection),
            patch.object(jobs, "EastMoneyNavSpider", return_value=fake_spider),
            patch.object(jobs, "upsert_nav_history", side_effect=save_rows),
        ):
            result = jobs.crawl_nav_history(
                options,
                DatabaseConfig("127.0.0.1", 3306, "root", "secret", "fund"),
                RequestConfig(0, 0, 1, 1),
            )

        self.assertEqual((1, 0, 5), result)
        self.assertEqual([3, 2], [len(rows) for rows in saved_batches])
        self.assertEqual(3, fake_spider.iter_kwargs["page_workers"])
        self.assertTrue(fake_connection.closed)

    def test_rejects_invalid_batch_size_before_database_access(self) -> None:
        options = jobs.NavHistoryOptions(
            selector=jobs.BatchSelector(fund_code="519674"),
            write_batch_size=0,
        )
        with self.assertRaisesRegex(ValueError, "NAV_WRITE_BATCH_SIZE"):
            jobs.crawl_nav_history(options)


class NavHistoryLoggingTest(unittest.TestCase):
    def test_nav_history_cli_enables_sql_and_parameter_logging_by_default(self) -> None:
        args = cli.build_parser().parse_args(["nav-history", "--fund-code", "519674"])
        with (
            patch.dict(
                os.environ,
                {
                    "LOG_SQL": "0",
                    "LOG_SQL_PARAMS": "0",
                    "LOG_SQL_MAX_PARAMS": "3",
                    "NAV_WRITE_BATCH_SIZE": "200",
                },
                clear=False,
            ),
            patch.object(jobs, "crawl_nav_history", return_value=(1, 0, 1)),
        ):
            cli.run_command(args)
            self.assertEqual("1", os.environ["LOG_SQL"])
            self.assertEqual("1", os.environ["LOG_SQL_PARAMS"])
            self.assertEqual("200", os.environ["LOG_SQL_MAX_PARAMS"])

    def test_sql_log_contains_statement_parameters_and_omitted_count(self) -> None:
        with (
            patch.dict(
                os.environ,
                {"LOG_SQL": "1", "LOG_SQL_PARAMS": "1", "LOG_SQL_MAX_PARAMS": "2"},
                clear=False,
            ),
            self.assertLogs("db", level="INFO") as captured,
        ):
            log_write_sql(
                "upsert_nav_history",
                "INSERT INTO fund_nav_history VALUES (%s, %s)",
                [("519674", "20260731"), ("519674", "20260730"), ("519674", "20260729")],
            )

        messages = "\n".join(captured.output)
        self.assertIn("statement=INSERT INTO fund_nav_history VALUES (%s, %s)", messages)
        self.assertIn("params=[('519674', '20260731'), ('519674', '20260730')]", messages)
        self.assertIn("omitted_rows=1", messages)


if __name__ == "__main__":
    unittest.main()
