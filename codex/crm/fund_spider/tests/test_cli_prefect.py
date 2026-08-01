from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import call
from unittest.mock import patch

import yaml


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import cli  # noqa: E402
import jobs  # noqa: E402
import prefect_flows  # noqa: E402
from runtime import task_runner  # noqa: E402
from settings import normalize_query_date  # noqa: E402


class CliTest(unittest.TestCase):
    def test_exposes_new_fund_commands(self) -> None:
        parser = cli.build_parser()

        self.assertEqual("basic", parser.parse_args(["basic"]).command)
        self.assertEqual(
            "nav-performance",
            parser.parse_args(["nav-performance"]).command,
        )
        self.assertEqual("current", parser.parse_args(["score"]).mode)
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
        history_alias = parser.parse_args(
            [
                "nav-history",
                "--fund_code",
                "000001",
                "--nav-page-workers",
                "8",
                "--nav-write-batch-size",
                "500",
            ]
        )
        self.assertEqual("000001", history_alias.FUND_CODE)
        self.assertEqual("8", history_alias.NAV_PAGE_WORKERS)
        self.assertEqual("500", history_alias.NAV_WRITE_BATCH_SIZE)

    def test_old_commands_are_removed(self) -> None:
        parser = cli.build_parser()
        for command in (
            "fund-list",
            "nav",
            "profile-nav",
            "rating-list",
            "all",
            "daily",
            "schedule",
            "web",
        ):
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


class PrefectTaskTest(unittest.TestCase):
    def prefect_config(self) -> dict:
        path = Path(__file__).resolve().parents[1] / "prefect.yaml"
        return yaml.safe_load(path.read_text(encoding="utf-8"))

    def test_prefect_deployments_cover_all_business_jobs(self) -> None:
        config = self.prefect_config()
        deployments = {row["name"]: row for row in config["deployments"]}

        self.assertEqual(
            {
                "morning-fund-refresh",
                "evening-nav-performance",
                "feature-refresh-manual",
                "score-pipeline",
                "sina-news",
                "stock-cn",
                "stock-hk",
            },
            set(deployments),
        )
        self.assertEqual(
            120,
            deployments["sina-news"]["schedules"][0]["interval"],
        )
        self.assertTrue(
            all(
                schedule["timezone"] == "Asia/Shanghai"
                for deployment in deployments.values()
                for schedule in deployment.get("schedules", [])
            )
        )
        self.assertTrue(
            all(
                deployment["concurrency_limit"]["limit"] == 1
                for deployment in deployments.values()
            )
        )
        self.assertTrue(
            all(
                deployment.get("paused") is False
                for deployment in deployments.values()
                if deployment.get("schedules")
            )
        )

    def test_morning_flow_runs_nav_before_feature(self) -> None:
        with patch.object(prefect_flows, "run_business_job") as run_job:
            prefect_flows.morning_fund_refresh_flow.fn(dry_run=True)

        self.assertEqual(
            [
                call("nav-performance", dry_run=True),
                call("feature", dry_run=True),
            ],
            run_job.call_args_list,
        )

    def test_job_failure_does_not_stop_following_job(self) -> None:
        statuses: list[tuple[str, str]] = []
        with tempfile.TemporaryDirectory() as temp_dir:
            log_dir = Path(temp_dir)
            lock_file = log_dir / "scheduler.lock"
            with (
                patch.object(task_runner, "LOG_DIR", log_dir),
                patch.object(task_runner, "LOCK_FILE", lock_file),
                patch.object(
                    task_runner,
                    "run_subprocess_job",
                    side_effect=[1, 0],
                ) as run_job,
            ):
                succeeded = task_runner.run_scheduled_jobs(
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
        built = task_runner.build_jobs(
            ("nav-performance", "feature", "score", "nav-performance")
        )
        self.assertEqual(["nav-performance", "feature", "score"], [job.name for job in built])
        self.assertEqual(
            ["./bin/run_nav_performance.sh", "./bin/run_feature.sh", "./bin/run_score.sh"],
            [job.script for job in built],
        )

    def test_all_migrated_job_commands_are_available(self) -> None:
        built = task_runner.build_jobs(
            (
                "nav-performance",
                "feature",
                "score",
                "sina-news",
                "stock-cn",
                "stock-hk",
            )
        )
        self.assertEqual(
            [
                "nav-performance",
                "feature",
                "score",
                "sina-news",
                "stock-cn",
                "stock-hk",
            ],
            [job.name for job in built],
        )
        self.assertTrue(all("--foreground" in job.args for job in built))


if __name__ == "__main__":
    unittest.main()
