from __future__ import annotations

import sys
import tempfile
import unittest
from datetime import datetime
from pathlib import Path
from unittest.mock import patch
from zoneinfo import ZoneInfo


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import cli  # noqa: E402
import jobs  # noqa: E402
from runtime import scheduler  # noqa: E402
from settings import normalize_query_date  # noqa: E402


TIMEZONE = ZoneInfo("Asia/Shanghai")


class CliTest(unittest.TestCase):
    def test_exposes_new_fund_commands(self) -> None:
        parser = cli.build_parser()

        self.assertEqual("basic", parser.parse_args(["basic"]).command)
        self.assertEqual(
            "nav-performance",
            parser.parse_args(["nav-performance"]).command,
        )
        history = parser.parse_args(
            [
                "nav-history",
                "--fund-code",
                "519674",
                "--start-date",
                "20260701",
                "--end-date",
                "2026-07-28",
            ]
        )
        self.assertEqual("519674", history.FUND_CODE)
        self.assertEqual("20260701", history.NAV_START_DATE)
        self.assertEqual("2026-07-28", history.NAV_END_DATE)

    def test_old_commands_are_removed(self) -> None:
        parser = cli.build_parser()
        for command in ("fund-list", "nav", "profile-nav", "rating-list", "all", "daily"):
            with self.subTest(command=command), self.assertRaises(SystemExit):
                parser.parse_args([command])

    def test_rating_modes_are_explicit(self) -> None:
        parser = cli.build_parser()
        self.assertEqual("current", parser.parse_args(["rating"]).mode)
        self.assertEqual(
            "history",
            parser.parse_args(["rating", "--mode", "history"]).mode,
        )


class DateRangeTest(unittest.TestCase):
    def test_accepts_supported_date_formats(self) -> None:
        self.assertEqual("2026-07-01", normalize_query_date("20260701"))
        self.assertEqual("2026-07-28", normalize_query_date("2026-07-28"))

    def test_accepts_open_and_closed_ranges(self) -> None:
        jobs.validate_date_range("", "")
        jobs.validate_date_range("2026-07-01", "")
        jobs.validate_date_range("", "2026-07-28")
        jobs.validate_date_range("2026-07-01", "2026-07-28")

    def test_rejects_reversed_range(self) -> None:
        with self.assertRaisesRegex(ValueError, "must not be later"):
            jobs.validate_date_range("2026-07-29", "2026-07-28")

    def test_rejects_invalid_calendar_date(self) -> None:
        with self.assertRaisesRegex(ValueError, "invalid calendar date"):
            normalize_query_date("20260230")


class SchedulerTest(unittest.TestCase):
    def test_morning_trigger_combines_nav_and_feature(self) -> None:
        trigger = scheduler.next_scheduled_trigger(
            [(8, 0), (21, 0)],
            (8, 0),
            datetime(2026, 7, 28, 7, 30, tzinfo=TIMEZONE),
        )

        self.assertEqual(datetime(2026, 7, 28, 8, 0, tzinfo=TIMEZONE), trigger.run_at)
        self.assertEqual(("nav-performance", "feature"), trigger.jobs)

    def test_evening_and_cross_day_triggers(self) -> None:
        evening = scheduler.next_scheduled_trigger(
            [(8, 0), (21, 0)],
            (8, 0),
            datetime(2026, 7, 28, 10, 0, tzinfo=TIMEZONE),
        )
        next_day = scheduler.next_scheduled_trigger(
            [(8, 0), (21, 0)],
            (8, 0),
            datetime(2026, 7, 28, 22, 0, tzinfo=TIMEZONE),
        )

        self.assertEqual(("nav-performance",), evening.jobs)
        self.assertEqual(datetime(2026, 7, 28, 21, 0, tzinfo=TIMEZONE), evening.run_at)
        self.assertEqual(datetime(2026, 7, 29, 8, 0, tzinfo=TIMEZONE), next_day.run_at)

    def test_job_failure_does_not_stop_following_job(self) -> None:
        statuses: list[tuple[str, str]] = []
        with tempfile.TemporaryDirectory() as temp_dir:
            log_dir = Path(temp_dir)
            lock_file = log_dir / "scheduler.lock"
            with (
                patch.object(scheduler, "LOG_DIR", log_dir),
                patch.object(scheduler, "LOCK_FILE", lock_file),
                patch.object(
                    scheduler,
                    "run_subprocess_job",
                    side_effect=[1, 0],
                ) as run_job,
            ):
                succeeded = scheduler.run_scheduled_jobs(
                    ("nav-performance", "feature"),
                    status_callback=lambda name, status: statuses.append((name, status)),
                )

        self.assertFalse(succeeded)
        self.assertEqual(2, run_job.call_count)
        self.assertEqual(
            [("nav-performance", "failed"), ("feature", "success")],
            statuses,
        )

    def test_duplicate_manual_jobs_are_deduplicated(self) -> None:
        built = scheduler.build_jobs(
            ("nav-performance", "feature", "nav-performance")
        )
        self.assertEqual(["nav-performance", "feature"], [job.name for job in built])
        self.assertEqual(
            ["./bin/run_nav_performance.sh", "./bin/run_feature.sh"],
            [job.script for job in built],
        )


if __name__ == "__main__":
    unittest.main()
