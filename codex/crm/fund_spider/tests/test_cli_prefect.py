from __future__ import annotations

import os
import sys
import tempfile
import unittest
import uuid
from datetime import datetime, timezone
from pathlib import Path
from unittest.mock import call
from unittest.mock import MagicMock
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
        scheduled_feature = parser.parse_args(
            ["feature", "--fund-limit", "2000", "--stale-first", "1"]
        )
        self.assertEqual("2000", scheduled_feature.FUND_LIMIT)
        self.assertEqual("1", scheduled_feature.FEATURE_STALE_FIRST)

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
                "ops-janitor",
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
                for name, deployment in deployments.items()
                if name != "ops-janitor"
            )
        )
        self.assertEqual(2, deployments["ops-janitor"]["concurrency_limit"]["limit"])
        self.assertTrue(
            all(
                deployment.get("paused") is False
                for deployment in deployments.values()
                if deployment.get("schedules")
            )
        )
        for name in ("sina-news", "stock-cn", "stock-hk"):
            self.assertEqual(
                "CANCEL_NEW",
                deployments[name]["concurrency_limit"]["collision_strategy"],
            )
            self.assertEqual("realtime", deployments[name]["work_pool"]["work_queue_name"])
        for name in (
            "morning-fund-refresh",
            "evening-nav-performance",
            "feature-refresh-manual",
            "score-pipeline",
        ):
            self.assertEqual("batch", deployments[name]["work_pool"]["work_queue_name"])
        janitor = deployments["ops-janitor"]
        self.assertEqual(
            600,
            janitor["schedules"][0]["interval"],
        )
        self.assertFalse(janitor["paused"])
        self.assertEqual("batch", janitor["work_pool"]["work_queue_name"])
        self.assertEqual(
            "prefect_flows.py:ops_janitor_flow",
            janitor["entrypoint"],
        )

        score_schedules = deployments["score-pipeline"]["schedules"]
        self.assertEqual(
            {("0 10 * * *", "daily-1000"), ("30 22 * * *", "daily-2230")},
            {(schedule["cron"], schedule["slug"]) for schedule in score_schedules},
        )
        self.assertTrue(
            all(schedule["timezone"] == "Asia/Shanghai" for schedule in score_schedules)
        )

    def test_morning_flow_runs_nav_before_feature(self) -> None:
        with patch.object(prefect_flows, "run_business_job") as run_job:
            prefect_flows.morning_fund_refresh_flow.fn(dry_run=True)

        self.assertEqual(
            [
                call("nav-performance", dry_run=True),
                call("feature-scheduled", dry_run=True),
            ],
            run_job.call_args_list,
        )

    def test_stale_realtime_run_detection(self) -> None:
        scheduled = datetime(2026, 8, 3, 1, 0, tzinfo=timezone.utc)
        self.assertFalse(
            prefect_flows.scheduled_run_is_stale(
                600,
                scheduled_start_time=scheduled,
                now=datetime(2026, 8, 3, 1, 9, 59, tzinfo=timezone.utc),
            )
        )
        self.assertTrue(
            prefect_flows.scheduled_run_is_stale(
                600,
                scheduled_start_time=scheduled,
                now=datetime(2026, 8, 3, 1, 10, 1, tzinfo=timezone.utc),
            )
        )

    def test_job_failure_does_not_stop_following_job(self) -> None:
        statuses: list[tuple[str, str]] = []
        with tempfile.TemporaryDirectory() as temp_dir:
            log_dir = Path(temp_dir)
            with (
                patch.object(task_runner, "LOG_DIR", log_dir / "jobs"),
                patch.object(task_runner, "LOCK_DIR", log_dir / "locks"),
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

    def test_scheduled_feature_batch_is_bounded_and_stale_first(self) -> None:
        with patch.dict("os.environ", {"FEATURE_SCHEDULE_FUND_LIMIT": "321"}):
            job = task_runner.build_jobs(("feature-scheduled",))[0]

        self.assertEqual("feature", job.lock_key)
        self.assertEqual(
            ["--foreground", "--fund-limit", "321", "--stale-first", "1"],
            job.args,
        )

    def test_different_job_locks_do_not_block_each_other(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            log_root = Path(temp_dir)
            feature_job = task_runner.build_jobs(("feature",))[0]
            feature_lock = log_root / "locks" / "feature.lock"
            feature_lock.parent.mkdir(parents=True)
            with (
                patch.object(task_runner, "LOG_DIR", log_root / "jobs"),
                patch.object(task_runner, "LOCK_DIR", log_root / "locks"),
            ):
                lock_fd = task_runner.acquire_lock(feature_lock)
                self.assertIsNotNone(lock_fd)
                try:
                    with patch.object(task_runner, "run_subprocess_job", return_value=0):
                        self.assertTrue(task_runner.run_scheduled_jobs(("stock-cn",)))
                finally:
                    task_runner.release_lock(lock_fd, task_runner.lock_file_for(feature_job))

    def test_stale_lock_is_reclaimed(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            lock_file = Path(temp_dir) / "stale.lock"
            lock_file.write_text("999999", encoding="ascii")
            with patch.object(task_runner, "process_is_running", return_value=False):
                lock_fd = task_runner.acquire_lock(lock_file)
            self.assertIsNotNone(lock_fd)
            task_runner.release_lock(lock_fd, lock_file)

    def test_job_logs_are_split_by_job_and_day(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            log_dir = Path(temp_dir) / "jobs"
            job = task_runner.build_jobs(("stock-cn",))[0]
            with patch.object(task_runner, "LOG_DIR", log_dir):
                log_file = task_runner.log_file_for(
                    job,
                    datetime(2026, 8, 3, 9, 30, tzinfo=timezone.utc),
                )

            self.assertEqual(log_dir / "stock-cn" / "2026-08-03.log", log_file)
            self.assertTrue(log_file.parent.is_dir())

    def test_old_job_logs_are_pruned(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            log_dir = Path(temp_dir)
            old_log = log_dir / "2026-07-01.log"
            current_log = log_dir / "2026-08-03.log"
            old_log.write_text("old", encoding="utf-8")
            current_log.write_text("current", encoding="utf-8")
            old_timestamp = datetime(2026, 7, 1, tzinfo=timezone.utc).timestamp()
            current_timestamp = datetime(2026, 8, 3, tzinfo=timezone.utc).timestamp()
            os.utime(old_log, (old_timestamp, old_timestamp))
            os.utime(current_log, (current_timestamp, current_timestamp))

            with patch.dict("os.environ", {"CRM_LOG_RETENTION_DAYS": "14"}):
                task_runner.prune_old_logs(
                    log_dir,
                    datetime(2026, 8, 3, tzinfo=timezone.utc),
                )

            self.assertFalse(old_log.exists())
            self.assertTrue(current_log.exists())

    def test_all_migrated_job_commands_are_available(self) -> None:
        built = task_runner.build_jobs(
            (
                "nav-performance",
                "feature",
                "feature-scheduled",
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
                "feature-scheduled",
                "score",
                "sina-news",
                "stock-cn",
                "stock-hk",
            ],
            [job.name for job in built],
        )
        self.assertTrue(all("--foreground" in job.args for job in built))

    def test_stale_first_feature_selection_uses_refresh_state(self) -> None:
        connection = MagicMock()
        options = jobs.FeatureOptions(
            selector=jobs.BatchSelector(fund_limit=25),
            stale_first=True,
        )
        with (
            patch.object(jobs, "connect", return_value=connection),
            patch.object(
                jobs,
                "list_fund_codes_for_refresh",
                return_value=["000001", "000002"],
            ) as select_codes,
        ):
            selected = jobs.load_feature_fund_codes(MagicMock(), options)

        self.assertEqual(["000001", "000002"], selected)
        select_codes.assert_called_once_with(
            connection,
            data_type="feature",
            limit=25,
        )
        connection.close.assert_called_once()


class OpsJanitorTest(unittest.TestCase):
    def make_run(self, name: str) -> MagicMock:
        run = MagicMock()
        run.id = uuid.uuid4()
        run.name = name
        run.expected_start_time = datetime(2026, 8, 19, 2, 35, tzinfo=timezone.utc)
        run.start_time = datetime(2026, 8, 19, 2, 35, tzinfo=timezone.utc)
        return run

    def test_reaps_stale_running_runs(self) -> None:
        stale_a = self.make_run("stock-cn-refresh")
        stale_b = self.make_run("stock-hk-refresh")
        client = MagicMock()
        client.read_flow_runs.side_effect = [[stale_a, stale_b], []]

        with patch.object(prefect_flows, "FAST_FLOW_NAMES", ("sina-news-refresh",)):
            reaped = prefect_flows.reap_stale_running_runs(
                client,
                max_age_minutes=30,
                now=datetime(2026, 8, 19, 3, 5, tzinfo=timezone.utc),
            )

        self.assertEqual([str(stale_a.id), str(stale_b.id)], reaped)
        self.assertEqual(2, client.set_flow_run_state.call_count)
        self.assertEqual(2, client.read_flow_runs.call_count)
        _, kwargs = client.read_flow_runs.call_args_list[0]
        self.assertEqual(
            ["sina-news-refresh"],
            list(kwargs["flow_filter"].name.any_),
        )
        state_type = list(kwargs["flow_run_filter"].state.type.any_)[0]
        before = kwargs["flow_run_filter"].start_time.before_
        self.assertEqual("RUNNING", str(state_type).split(".")[-1])
        self.assertEqual(datetime(2026, 8, 19, 2, 35, tzinfo=timezone.utc), before)

    def test_dry_run_reads_without_writing(self) -> None:
        client = MagicMock()
        client.read_flow_runs.side_effect = [[self.make_run("stock-cn-refresh")], []]

        reaped = prefect_flows.reap_stale_running_runs(client, dry_run=True)

        self.assertEqual([], reaped)
        self.assertEqual(2, client.read_flow_runs.call_count)
        client.set_flow_run_state.assert_not_called()

    def test_set_state_failure_does_not_block_remaining_runs(self) -> None:
        first = self.make_run("stock-cn-refresh")
        second = self.make_run("stock-hk-refresh")
        client = MagicMock()
        client.read_flow_runs.side_effect = [[first, second], []]
        client.set_flow_run_state.side_effect = [RuntimeError("transition aborted"), None]

        reaped = prefect_flows.reap_stale_running_runs(client)

        self.assertEqual([str(second.id)], reaped)
        self.assertEqual(2, client.set_flow_run_state.call_count)

    def test_reaps_stale_pending_runs(self) -> None:
        stale = self.make_run("sina-news-refresh")
        stale.start_time = None
        client = MagicMock()
        client.read_flow_runs.side_effect = [[], [stale]]

        reaped = prefect_flows.reap_stale_running_runs(
            client,
            now=datetime(2026, 8, 19, 3, 5, tzinfo=timezone.utc),
        )

        self.assertEqual([str(stale.id)], reaped)
        _, kwargs = client.read_flow_runs.call_args
        state_type = list(kwargs["flow_run_filter"].state.type.any_)[0]
        self.assertEqual("PENDING", str(state_type).split(".")[-1])
        self.assertEqual(
            datetime(2026, 8, 19, 2, 35, tzinfo=timezone.utc),
            kwargs["flow_run_filter"].expected_start_time.before_,
        )

    def test_reaps_stale_batch_runs_with_longer_timeout(self) -> None:
        stale = self.make_run("fund-morning-refresh")
        client = MagicMock()
        client.read_flow_runs.side_effect = [[stale], []]

        reaped = prefect_flows.reap_stale_running_runs(
            client,
            max_age_minutes=prefect_flows.DEFAULT_BATCH_STALE_RUN_MINUTES,
            flow_names=prefect_flows.BATCH_FLOW_NAMES,
            now=datetime(2026, 8, 19, 10, 35, tzinfo=timezone.utc),
        )

        self.assertEqual([str(stale.id)], reaped)
        _, kwargs = client.read_flow_runs.call_args_list[0]
        self.assertEqual(
            list(prefect_flows.BATCH_FLOW_NAMES),
            list(kwargs["flow_filter"].name.any_),
        )
        self.assertEqual(
            datetime(2026, 8, 19, 2, 35, tzinfo=timezone.utc),
            kwargs["flow_run_filter"].start_time.before_,
        )

    def test_releases_orphaned_deployment_slots(self) -> None:
        deployment = MagicMock()
        deployment.id = uuid.uuid4()
        deployment.name = "morning-fund-refresh"
        deployment.global_concurrency_limit.name = "deployment:test"
        deployment.global_concurrency_limit.active_slots = 1
        deployment.global_concurrency_limit.updated = datetime(
            2026, 8, 19, 2, 0, tzinfo=timezone.utc
        )
        client = MagicMock()
        client.read_deployments.return_value = [deployment]
        client.read_flow_runs.return_value = []

        released = prefect_flows.release_orphaned_deployment_slots(
            client,
            now=datetime(2026, 8, 19, 2, 15, tzinfo=timezone.utc),
        )

        self.assertEqual(["deployment:test"], released)
        client.release_concurrency_slots.assert_called_once()
        self.assertEqual(1, client.release_concurrency_slots.call_args.kwargs["slots"])

    def test_keeps_slot_held_by_running_run(self) -> None:
        deployment = MagicMock()
        deployment.id = uuid.uuid4()
        deployment.name = "stock-hk-refresh"
        deployment.global_concurrency_limit.name = "deployment:test"
        deployment.global_concurrency_limit.active_slots = 1
        deployment.global_concurrency_limit.updated = datetime(
            2026, 8, 19, 2, 0, tzinfo=timezone.utc
        )
        run = MagicMock()
        run.state_name = "Running"
        client = MagicMock()
        client.read_deployments.return_value = [deployment]
        client.read_flow_runs.return_value = [run]

        released = prefect_flows.release_orphaned_deployment_slots(
            client,
            now=datetime(2026, 8, 19, 2, 15, tzinfo=timezone.utc),
        )

        self.assertEqual([], released)
        client.release_concurrency_slots.assert_not_called()

    def test_ignores_recent_concurrency_slot_changes(self) -> None:
        deployment = MagicMock()
        deployment.id = uuid.uuid4()
        deployment.name = "stock-hk-refresh"
        deployment.global_concurrency_limit.name = "deployment:test"
        deployment.global_concurrency_limit.active_slots = 1
        deployment.global_concurrency_limit.updated = datetime(
            2026, 8, 19, 3, 10, tzinfo=timezone.utc
        )
        client = MagicMock()
        client.read_deployments.return_value = [deployment]

        released = prefect_flows.release_orphaned_deployment_slots(
            client,
            now=datetime(2026, 8, 19, 3, 15, tzinfo=timezone.utc),
        )

        self.assertEqual([], released)
        client.read_flow_runs.assert_not_called()
        client.release_concurrency_slots.assert_not_called()

    def test_flow_reaps_realtime_and_batch_with_separate_thresholds(self) -> None:
        client = MagicMock()
        client.read_deployments.return_value = []
        client_context = MagicMock()
        client_context.__enter__.return_value = client
        client_context.__exit__.return_value = False

        with (
            patch.object(prefect_flows, "get_client", return_value=client_context),
            patch.object(
                prefect_flows,
                "reap_stale_running_runs",
                side_effect=[["realtime-run"], ["batch-run"]],
            ) as reap,
        ):
            reaped = prefect_flows.reap_stale_flow_runs.fn(
                max_age_minutes=30,
                batch_max_age_minutes=480,
            )

        self.assertEqual(["realtime-run", "batch-run"], reaped)
        self.assertEqual(2, reap.call_count)
        first_call, second_call = reap.call_args_list
        self.assertEqual(30, first_call.kwargs["max_age_minutes"])
        self.assertEqual(prefect_flows.FAST_FLOW_NAMES, first_call.kwargs["flow_names"])
        self.assertEqual(480, second_call.kwargs["max_age_minutes"])
        self.assertEqual(prefect_flows.BATCH_FLOW_NAMES, second_call.kwargs["flow_names"])

    def test_rejects_non_positive_max_age(self) -> None:
        with self.assertRaisesRegex(ValueError, "max_age_minutes must be positive"):
            prefect_flows.reap_stale_running_runs(MagicMock(), max_age_minutes=0)


if __name__ == "__main__":
    unittest.main()
