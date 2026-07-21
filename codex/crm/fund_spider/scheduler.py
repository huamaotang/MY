from __future__ import annotations

import logging
import os
import subprocess
import sys
import time
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from pathlib import Path

from settings import load_env_file, normalize_query_date, parse_bool


BASE_DIR = Path(__file__).resolve().parent
LOG_DIR = BASE_DIR / "logs"
LOCK_FILE = LOG_DIR / "daily_fund_job.lock"

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class Job:
    name: str
    script: str
    enabled: bool
    env: dict[str, str]
    args: list[str] = field(default_factory=list)


def main() -> None:
    LOG_DIR.mkdir(parents=True, exist_ok=True)
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s - %(message)s",
    )
    load_env_file(BASE_DIR / ".env")

    schedule_time = parse_schedule_time(os.getenv("SCHEDULE_TIME", "20:00"))
    run_on_start = parse_bool(os.getenv("SCHEDULER_RUN_ON_START", "0"))
    run_once = parse_bool(os.getenv("SCHEDULER_ONCE", "0"))
    dry_run = parse_bool(os.getenv("SCHEDULER_DRY_RUN", "0"))

    logger.info(
        "scheduler started, schedule_time=%s:%s, run_on_start=%s, run_once=%s, dry_run=%s",
        f"{schedule_time[0]:02d}",
        f"{schedule_time[1]:02d}",
        run_on_start,
        run_once,
        dry_run,
    )

    if run_on_start or run_once:
        run_daily_jobs(dry_run=dry_run)
        if run_once:
            return

    while True:
        next_time = next_run_at(schedule_time)
        logger.info("next daily fund job will run at %s", next_time.strftime("%Y-%m-%d %H:%M:%S"))
        sleep_until(next_time)
        run_daily_jobs(dry_run=dry_run)


def run_daily_jobs(dry_run: bool = False, extra_env: dict[str, str] | None = None) -> bool:
    LOG_DIR.mkdir(parents=True, exist_ok=True)
    started_at = datetime.now()
    log_file = LOG_DIR / f"daily_fund_job_{started_at.strftime('%Y%m%d_%H%M%S')}.log"

    lock_fd = acquire_lock()
    if lock_fd is None:
        logger.warning("another daily fund job is running, skip this trigger")
        return False

    succeeded = True
    try:
        runtime_env = extra_env or {}
        jobs = build_jobs(started_at, runtime_env)
        logger.info("daily fund job started, log_file=%s", log_file)
        with log_file.open("a", encoding="utf-8") as log_handle:
            write_log(log_handle, f"daily fund job started at {started_at:%Y-%m-%d %H:%M:%S}")
            for job in jobs:
                if not job.enabled:
                    write_log(log_handle, f"[SKIP] {job.name}")
                    continue
                if dry_run:
                    command = " ".join([sys.executable, job.script, *job.args])
                    write_log(log_handle, f"[DRY-RUN] {job.name}: {command}")
                    if job.env:
                        write_log(log_handle, f"[DRY-RUN] env: {job.env}")
                    continue

                return_code = run_subprocess_job(job, log_handle, runtime_env)
                if return_code != 0:
                    succeeded = False
                    write_log(log_handle, f"[FAILED] {job.name}, return_code={return_code}")
                    break
            write_log(log_handle, f"daily fund job finished at {datetime.now():%Y-%m-%d %H:%M:%S}")
        return succeeded
    finally:
        release_lock(lock_fd)


def build_jobs(now: datetime, env: dict[str, str] | None = None) -> list[Job]:
    env = env or {}
    nav_start_date = resolve_nav_start_date(now, env)
    profile_nav_enabled = parse_bool(get_config_value("DAILY_CRAWL_PROFILE_NAV", "1", env))
    daily_env = {
        "DAILY_CRAWL_FUND_LIST": get_config_value("DAILY_CRAWL_FUND_LIST", "1", env),
        "DAILY_CRAWL_PROFILE": get_config_value("DAILY_CRAWL_PROFILE", "1", env) if profile_nav_enabled else "0",
        "DAILY_CRAWL_NAV": get_config_value("DAILY_CRAWL_NAV", "1", env) if profile_nav_enabled else "0",
        "DAILY_CRAWL_FEATURE": get_config_value("DAILY_CRAWL_FEATURE", "1", env),
        "DAILY_CRAWL_RATING": get_config_value("DAILY_CRAWL_RATING", "1", env),
        "DAILY_CRAWL_HOLDINGS": get_config_value("DAILY_CRAWL_HOLDINGS", "1", env),
        "DAILY_NAV_REFRESH_DAYS": get_config_value("DAILY_NAV_REFRESH_DAYS", "1", env),
        "DAILY_PROFILE_REFRESH_DAYS": get_config_value("DAILY_PROFILE_REFRESH_DAYS", "30", env),
        "DAILY_FEATURE_REFRESH_DAYS": get_config_value("DAILY_FEATURE_REFRESH_DAYS", "7", env),
        "DAILY_RATING_REFRESH_DAYS": get_config_value("DAILY_RATING_REFRESH_DAYS", "7", env),
        "DAILY_HOLDING_REFRESH_DAYS": get_config_value("DAILY_HOLDING_REFRESH_DAYS", "7", env),
        "DAILY_RATING_MAX_PAGES": get_config_value("DAILY_RATING_MAX_PAGES", "1", env),
        "NAV_START_DATE": nav_start_date,
    }
    nav_end_date = get_config_value("DAILY_NAV_END_DATE", get_config_value("NAV_END_DATE", "", env), env).strip()
    if nav_end_date:
        daily_env["NAV_END_DATE"] = normalize_query_date(nav_end_date)

    job_enabled = any(
        parse_bool(daily_env[name])
        for name in (
            "DAILY_CRAWL_FUND_LIST",
            "DAILY_CRAWL_PROFILE",
            "DAILY_CRAWL_NAV",
            "DAILY_CRAWL_FEATURE",
            "DAILY_CRAWL_RATING",
            "DAILY_CRAWL_HOLDINGS",
        )
    )

    return [
        Job(
            name="daily_update",
            script="cli.py",
            args=["daily"],
            enabled=job_enabled,
            env=daily_env,
        ),
    ]


def run_subprocess_job(job: Job, log_handle, runtime_env: dict[str, str] | None = None) -> int:
    env = os.environ.copy()
    if runtime_env:
        env.update(runtime_env)
    env.update(job.env)
    env["PYTHONUNBUFFERED"] = "1"

    command = [sys.executable, job.script, *job.args]
    write_log(log_handle, f"[START] {job.name}: {' '.join(command)}")
    if job.env:
        write_log(log_handle, f"[ENV] {job.env}")

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


def resolve_nav_start_date(now: datetime, env: dict[str, str] | None = None) -> str:
    env = env or {}
    explicit = get_config_value("DAILY_NAV_START_DATE", "", env).strip()
    if explicit:
        return normalize_query_date(explicit)

    inherited = get_config_value("NAV_START_DATE", "", env).strip()
    if inherited:
        return normalize_query_date(inherited)

    lookback_days = int(get_config_value("DAILY_NAV_LOOKBACK_DAYS", "10", env))
    start = now.date() - timedelta(days=lookback_days)
    return start.strftime("%Y-%m-%d")


def get_config_value(name: str, default: str, env: dict[str, str]) -> str:
    value = env.get(name)
    if value is not None:
        return value
    return os.getenv(name, default)


def parse_schedule_time(value: str) -> tuple[int, int]:
    parts = value.strip().split(":")
    if len(parts) != 2:
        raise ValueError("SCHEDULE_TIME must use HH:MM format")
    hour = int(parts[0])
    minute = int(parts[1])
    if hour < 0 or hour > 23 or minute < 0 or minute > 59:
        raise ValueError("SCHEDULE_TIME must be a valid local time")
    return hour, minute


def next_run_at(schedule_time: tuple[int, int]) -> datetime:
    now = datetime.now()
    candidate = now.replace(hour=schedule_time[0], minute=schedule_time[1], second=0, microsecond=0)
    if candidate <= now:
        candidate += timedelta(days=1)
    return candidate


def sleep_until(target: datetime) -> None:
    while True:
        seconds = (target - datetime.now()).total_seconds()
        if seconds <= 0:
            return
        time.sleep(min(seconds, 60))


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
    line = f"{datetime.now():%Y-%m-%d %H:%M:%S} {message}"
    print(line, flush=True)
    log_handle.write(line + "\n")
    log_handle.flush()
if __name__ == "__main__":
    main()
