from __future__ import annotations

import logging
import os
import subprocess
import time
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from pathlib import Path
from typing import Callable
from zoneinfo import ZoneInfo

from settings import load_env_file, parse_bool


BASE_DIR = Path(__file__).resolve().parents[1]
LOG_DIR = BASE_DIR / "logs"
LOCK_FILE = LOG_DIR / "fund_scheduler.lock"
TIMEZONE = ZoneInfo("Asia/Shanghai")

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class Job:
    name: str
    script: str
    args: list[str]
    env: dict[str, str] = field(default_factory=dict)


@dataclass(frozen=True)
class ScheduledTrigger:
    run_at: datetime
    jobs: tuple[str, ...]


def main() -> None:
    LOG_DIR.mkdir(parents=True, exist_ok=True)
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s - %(message)s",
    )
    load_env_file(BASE_DIR / ".env")

    nav_times = parse_schedule_times(
        os.getenv("NAV_PERFORMANCE_SCHEDULE_TIMES", "08:00,21:00")
    )
    feature_time = parse_schedule_time(os.getenv("FEATURE_SCHEDULE_TIME", "08:00"))
    run_on_start = parse_bool(os.getenv("SCHEDULER_RUN_ON_START", "0"))
    run_once = parse_bool(os.getenv("SCHEDULER_ONCE", "0"))
    dry_run = parse_bool(os.getenv("SCHEDULER_DRY_RUN", "0"))
    trigger_name = os.getenv("SCHEDULER_TRIGGER", "all").strip().lower() or "all"

    if trigger_name not in {"morning", "evening", "all"}:
        raise ValueError("SCHEDULER_TRIGGER must be morning, evening, or all")

    logger.info(
        "scheduler started timezone=Asia/Shanghai nav_times=%s feature_time=%02d:%02d",
        ",".join(format_schedule_time(value) for value in nav_times),
        feature_time[0],
        feature_time[1],
    )

    if run_on_start or run_once:
        run_scheduled_jobs(
            jobs_for_manual_trigger(trigger_name),
            dry_run=dry_run,
        )
        if run_once:
            return

    while True:
        trigger = next_scheduled_trigger(nav_times, feature_time)
        logger.info(
            "next trigger run_at=%s jobs=%s",
            trigger.run_at.isoformat(),
            ",".join(trigger.jobs),
        )
        sleep_until(trigger.run_at)
        run_scheduled_jobs(trigger.jobs, dry_run=dry_run)


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
            args=[],
        ),
        "feature": Job(
            name="feature",
            script="./bin/run_feature.sh",
            args=[],
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
        return ("nav-performance",)
    if trigger_name == "all":
        return ("nav-performance", "feature")
    raise ValueError(f"unsupported trigger: {trigger_name}")


def next_scheduled_trigger(
    nav_times: list[tuple[int, int]],
    feature_time: tuple[int, int],
    now: datetime | None = None,
) -> ScheduledTrigger:
    reference = now or datetime.now(TIMEZONE)
    if reference.tzinfo is None:
        reference = reference.replace(tzinfo=TIMEZONE)
    else:
        reference = reference.astimezone(TIMEZONE)

    schedule: dict[tuple[int, int], list[str]] = {}
    for schedule_time in nav_times:
        schedule.setdefault(schedule_time, []).append("nav-performance")
    schedule.setdefault(feature_time, []).append("feature")

    candidates: list[ScheduledTrigger] = []
    for schedule_time, jobs in schedule.items():
        run_at = reference.replace(
            hour=schedule_time[0],
            minute=schedule_time[1],
            second=0,
            microsecond=0,
        )
        if run_at <= reference:
            run_at += timedelta(days=1)
        candidates.append(ScheduledTrigger(run_at=run_at, jobs=tuple(jobs)))
    return min(candidates, key=lambda item: item.run_at)


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


def format_schedule_time(value: tuple[int, int]) -> str:
    return f"{value[0]:02d}:{value[1]:02d}"


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


def sleep_until(target: datetime) -> None:
    while True:
        seconds = (target - datetime.now(TIMEZONE)).total_seconds()
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
    line = f"{datetime.now(TIMEZONE):%Y-%m-%d %H:%M:%S%z} {message}"
    print(line, flush=True)
    log_handle.write(line + "\n")
    log_handle.flush()


if __name__ == "__main__":
    main()
