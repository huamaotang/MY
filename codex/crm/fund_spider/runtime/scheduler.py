from __future__ import annotations

from functools import partial
import logging
import os
import subprocess
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Any, Callable
from zoneinfo import ZoneInfo

from apscheduler import __version__ as APSCHEDULER_VERSION
from apscheduler.executors.pool import ThreadPoolExecutor as APSchedulerThreadPool
from apscheduler.schedulers.base import BaseScheduler
from apscheduler.schedulers.blocking import BlockingScheduler
from apscheduler.triggers.cron import CronTrigger
from apscheduler.triggers.interval import IntervalTrigger

from settings import load_env_file, parse_bool


BASE_DIR = Path(__file__).resolve().parents[1]
LOG_DIR = BASE_DIR / "logs"
LOCK_FILE = LOG_DIR / "fund_scheduler.lock"
TIMEZONE = ZoneInfo("Asia/Shanghai")
SCHEDULER_ENGINE = f"APScheduler {APSCHEDULER_VERSION}"
JOB_DEFAULTS = {
    "coalesce": True,
    "max_instances": 1,
    "misfire_grace_time": 300,
}

logger = logging.getLogger(__name__)
JobRunner = Callable[[tuple[str, ...]], Any]


@dataclass(frozen=True)
class Job:
    name: str
    script: str
    args: list[str]
    env: dict[str, str] = field(default_factory=dict)


def main() -> None:
    LOG_DIR.mkdir(parents=True, exist_ok=True)
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s - %(message)s",
    )
    load_env_file(BASE_DIR / ".env")
    config = scheduler_config_from_env()
    run_on_start = parse_bool(os.getenv("SCHEDULER_RUN_ON_START", "0"))
    run_once = parse_bool(os.getenv("SCHEDULER_ONCE", "0"))
    trigger_name = os.getenv("SCHEDULER_TRIGGER", "all").strip().lower() or "all"

    if trigger_name not in {"morning", "evening", "news", "stock", "all"}:
        raise ValueError("SCHEDULER_TRIGGER must be morning, evening, news, stock, or all")

    logger.info(
        "%s started timezone=Asia/Shanghai nav_times=%s feature_time=%s "
        "score_time=%s sina_news=%s/%ss stock_market=%s/%ss",
        SCHEDULER_ENGINE,
        config["nav_performance_schedule_times"],
        config["feature_schedule_time"],
        config["score_schedule_time"],
        config["sina_news_enabled"],
        config["sina_news_interval_seconds"],
        config["stock_market_enabled"],
        config["stock_market_interval_seconds"],
    )

    if run_on_start or run_once:
        run_scheduled_jobs(
            jobs_for_manual_trigger(trigger_name),
            dry_run=config["dry_run"],
        )
        if run_once:
            return

    apscheduler = BlockingScheduler(
        timezone=TIMEZONE,
        executors={"default": APSchedulerThreadPool(max_workers=1)},
        job_defaults=JOB_DEFAULTS,
    )
    configure_apscheduler_jobs(
        apscheduler,
        config,
        partial(execute_job_group, dry_run=config["dry_run"]),
    )
    try:
        apscheduler.start()
    except (KeyboardInterrupt, SystemExit):
        logger.info("%s stopped", SCHEDULER_ENGINE)


def scheduler_config_from_env() -> dict[str, Any]:
    return {
        "enabled": True,
        "dry_run": parse_bool(os.getenv("SCHEDULER_DRY_RUN", "0")),
        "nav_performance_schedule_times": os.getenv(
            "NAV_PERFORMANCE_SCHEDULE_TIMES",
            "08:00,21:00",
        ),
        "feature_schedule_time": os.getenv("FEATURE_SCHEDULE_TIME", "08:00"),
        "feature_enabled": parse_bool(os.getenv("FEATURE_SCHEDULE_ENABLED", "1")),
        "score_schedule_time": os.getenv("SCORE_SCHEDULE_TIME", "22:30"),
        "sina_news_enabled": parse_bool(
            os.getenv("SINA_NEWS_SCHEDULE_ENABLED", "1")
        ),
        "sina_news_interval_seconds": parse_positive_seconds(
            os.getenv("SINA_NEWS_INTERVAL_SECONDS", "120"),
            "SINA_NEWS_INTERVAL_SECONDS",
        ),
        "stock_market_enabled": parse_bool(
            os.getenv("STOCK_MARKET_SCHEDULE_ENABLED", "1")
        ),
        "stock_market_interval_seconds": parse_positive_seconds(
            os.getenv("STOCK_MARKET_INTERVAL_SECONDS", "300"),
            "STOCK_MARKET_INTERVAL_SECONDS",
        ),
    }


def configure_apscheduler_jobs(
    apscheduler: BaseScheduler,
    config: dict[str, Any],
    runner: JobRunner,
) -> None:
    apscheduler.remove_all_jobs()
    if not config["enabled"]:
        return

    daily_groups: dict[tuple[int, int], list[str]] = {}
    for schedule_time in parse_schedule_times(
        str(config["nav_performance_schedule_times"])
    ):
        daily_groups.setdefault(schedule_time, []).append("nav-performance")
    if config["feature_enabled"]:
        feature_time = parse_schedule_time(str(config["feature_schedule_time"]))
        daily_groups.setdefault(feature_time, []).append("feature")
    score_time = parse_schedule_time(str(config["score_schedule_time"]))
    daily_groups.setdefault(score_time, []).append("score")

    for (hour, minute), job_names in sorted(daily_groups.items()):
        unique_names = tuple(dict.fromkeys(job_names))
        apscheduler.add_job(
            runner,
            trigger=CronTrigger(
                hour=hour,
                minute=minute,
                second=0,
                timezone=TIMEZONE,
            ),
            args=[unique_names],
            id=f"daily-{hour:02d}{minute:02d}",
            name=",".join(unique_names),
            replace_existing=True,
            coalesce=True,
            max_instances=1,
            misfire_grace_time=3600,
        )

    if config["sina_news_enabled"]:
        apscheduler.add_job(
            runner,
            trigger=IntervalTrigger(
                seconds=parse_positive_seconds(
                    config["sina_news_interval_seconds"],
                    "sina_news_interval_seconds",
                ),
                timezone=TIMEZONE,
            ),
            args=[("sina-news",)],
            id="interval-sina-news",
            name="sina-news",
            replace_existing=True,
            coalesce=True,
            max_instances=1,
            misfire_grace_time=120,
        )

    if config["stock_market_enabled"]:
        apscheduler.add_job(
            run_stock_market_poll,
            trigger=IntervalTrigger(
                seconds=parse_positive_seconds(
                    config["stock_market_interval_seconds"],
                    "stock_market_interval_seconds",
                ),
                timezone=TIMEZONE,
            ),
            args=[runner],
            id="interval-stock-market",
            name="stock-market-check",
            replace_existing=True,
            coalesce=True,
            max_instances=1,
            misfire_grace_time=120,
        )


def execute_job_group(job_names: tuple[str, ...], dry_run: bool = False) -> bool:
    succeeded = run_scheduled_jobs(job_names, dry_run=dry_run)
    if not succeeded:
        raise RuntimeError(f"scheduled job group failed: {','.join(job_names)}")
    return True


def run_stock_market_poll(
    runner: JobRunner,
    now: datetime | None = None,
) -> bool:
    job_names = stock_markets_at(now or datetime.now(TIMEZONE))
    if not job_names:
        logger.debug("stock market poll skipped outside configured trading windows")
        return False
    runner(job_names)
    return True


def scheduled_jobs_snapshot(apscheduler: BaseScheduler | None) -> list[dict[str, Any]]:
    if apscheduler is None:
        return []
    rows: list[dict[str, Any]] = []
    for job in apscheduler.get_jobs():
        next_run_time = getattr(job, "next_run_time", None)
        rows.append(
            {
                "id": job.id,
                "name": job.name,
                "next_run_time": (
                    next_run_time.astimezone(TIMEZONE).isoformat()
                    if next_run_time
                    else None
                ),
                "trigger": str(job.trigger),
            }
        )
    return sorted(
        rows,
        key=lambda row: (
            row["next_run_time"] is None,
            row["next_run_time"] or "",
            row["id"],
        ),
    )


def next_jobs_from_snapshot(
    scheduled_jobs: list[dict[str, Any]],
) -> tuple[str | None, list[str]]:
    runnable = [row for row in scheduled_jobs if row["next_run_time"]]
    if not runnable:
        return None, []
    next_run_time = runnable[0]["next_run_time"]
    names: list[str] = []
    for row in runnable:
        if row["next_run_time"] != next_run_time:
            break
        for name in str(row["name"]).split(","):
            if name and name not in names:
                names.append(name)
    return next_run_time, names


def run_scheduled_jobs(
    job_names: tuple[str, ...] | list[str],
    dry_run: bool = False,
    extra_env: dict[str, str] | None = None,
    status_callback: Callable[[str, str], None] | None = None,
) -> bool:
    LOG_DIR.mkdir(parents=True, exist_ok=True)
    started_at = datetime.now(TIMEZONE)
    log_file = LOG_DIR / f"fund_scheduler_{started_at.strftime('%Y%m%d_%H%M%S')}.log"
    lock_fd = acquire_lock()
    if lock_fd is None:
        logger.warning("status=skipped_overlap jobs=%s", ",".join(job_names))
        return False

    succeeded = True
    try:
        selected_jobs = build_jobs(job_names)
        with log_file.open("a", encoding="utf-8") as log_handle:
            write_log(log_handle, f"[TRIGGER] jobs={','.join(job_names)}")
            for job in selected_jobs:
                if dry_run:
                    command = " ".join([job.script, *job.args])
                    write_log(log_handle, f"[DRY-RUN] {job.name}: {command}")
                    if status_callback:
                        status_callback(job.name, "dry-run")
                    continue
                return_code = run_subprocess_job(
                    job,
                    log_handle,
                    runtime_env=extra_env,
                )
                if return_code != 0:
                    succeeded = False
                    write_log(
                        log_handle,
                        f"[FAILED] {job.name}, return_code={return_code}",
                    )
                    if status_callback:
                        status_callback(job.name, "failed")
                elif status_callback:
                    status_callback(job.name, "success")
            write_log(
                log_handle,
                f"[FINISH] status={'success' if succeeded else 'failed'}",
            )
        return succeeded
    finally:
        release_lock(lock_fd)


def build_jobs(job_names: tuple[str, ...] | list[str]) -> list[Job]:
    definitions = {
        "nav-performance": Job(
            name="nav-performance",
            script="./bin/run_nav_performance.sh",
            args=["--foreground"],
        ),
        "feature": Job(
            name="feature",
            script="./bin/run_feature.sh",
            args=["--foreground"],
        ),
        "score": Job(
            name="score",
            script="./bin/run_score.sh",
            args=["--foreground"],
        ),
        "sina-news": Job(
            name="sina-news",
            script="./bin/run_sina_news.sh",
            args=["--foreground"],
        ),
        "stock-cn": Job(
            name="stock-cn",
            script="./bin/run_stock.sh",
            args=["cn", "--foreground"],
        ),
        "stock-hk": Job(
            name="stock-hk",
            script="./bin/run_stock.sh",
            args=["hk", "--foreground"],
        ),
    }
    jobs = []
    for name in job_names:
        if name not in definitions:
            raise ValueError(f"unsupported scheduled job: {name}")
        if all(existing.name != name for existing in jobs):
            jobs.append(definitions[name])
    return jobs


def jobs_for_manual_trigger(trigger_name: str) -> tuple[str, ...]:
    if trigger_name == "morning":
        return ("nav-performance", "feature")
    if trigger_name == "evening":
        return ("nav-performance", "score")
    if trigger_name == "news":
        return ("sina-news",)
    if trigger_name == "stock":
        return ("stock-cn", "stock-hk")
    if trigger_name == "all":
        return (
            "nav-performance",
            "feature",
            "score",
            "sina-news",
            "stock-cn",
            "stock-hk",
        )
    raise ValueError(f"unsupported trigger: {trigger_name}")


def stock_markets_at(value: datetime) -> tuple[str, ...]:
    local = value.astimezone(TIMEZONE)
    if local.weekday() >= 5:
        return ()
    minute = local.hour * 60 + local.minute
    markets: list[str] = []
    if (
        9 * 60 + 15 <= minute <= 11 * 60 + 35
        or 12 * 60 + 55 <= minute <= 15 * 60 + 10
        or 15 * 60 + 15 <= minute <= 15 * 60 + 30
    ):
        markets.append("stock-cn")
    if (
        9 * 60 + 25 <= minute <= 12 * 60 + 5
        or 12 * 60 + 55 <= minute <= 16 * 60 + 10
    ):
        markets.append("stock-hk")
    return tuple(markets)


def parse_positive_seconds(value: str | int, field_name: str) -> int:
    parsed = int(value)
    if parsed < 1:
        raise ValueError(f"{field_name} must be greater than or equal to 1")
    return parsed


def parse_schedule_times(value: str) -> list[tuple[int, int]]:
    values = [part.strip() for part in value.split(",") if part.strip()]
    if not values:
        raise ValueError("NAV_PERFORMANCE_SCHEDULE_TIMES must contain at least one time")
    parsed = [parse_schedule_time(value) for value in values]
    return list(dict.fromkeys(parsed))


def parse_schedule_time(value: str) -> tuple[int, int]:
    parts = value.strip().split(":")
    if len(parts) != 2:
        raise ValueError("schedule time must use HH:MM format")
    hour = int(parts[0])
    minute = int(parts[1])
    if hour < 0 or hour > 23 or minute < 0 or minute > 59:
        raise ValueError("schedule time must be a valid local time")
    return hour, minute


def run_subprocess_job(
    job: Job,
    log_handle,
    runtime_env: dict[str, str] | None = None,
) -> int:
    env = os.environ.copy()
    if runtime_env:
        env.update(runtime_env)
    env.update(job.env)
    env["PYTHONUNBUFFERED"] = "1"
    command = [job.script, *job.args]
    write_log(log_handle, f"[START] {job.name}: {' '.join(command)}")
    process = subprocess.Popen(
        command,
        cwd=str(BASE_DIR),
        env=env,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        bufsize=1,
    )
    assert process.stdout is not None
    for line in process.stdout:
        text = line.rstrip("\n")
        print(text, flush=True)
        log_handle.write(text + "\n")
        log_handle.flush()
    return_code = process.wait()
    write_log(log_handle, f"[END] {job.name}, return_code={return_code}")
    return return_code


def acquire_lock() -> int | None:
    try:
        return os.open(str(LOCK_FILE), os.O_CREAT | os.O_EXCL | os.O_WRONLY)
    except FileExistsError:
        return None


def release_lock(lock_fd: int) -> None:
    os.close(lock_fd)
    try:
        LOCK_FILE.unlink()
    except FileNotFoundError:
        pass


def write_log(log_handle, message: str) -> None:
    line = f"{datetime.now(TIMEZONE):%Y-%m-%d %H:%M:%S%z} {message}"
    print(line, flush=True)
    log_handle.write(line + "\n")
    log_handle.flush()


if __name__ == "__main__":
    main()
