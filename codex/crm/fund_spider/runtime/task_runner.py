from __future__ import annotations

import logging
import os
import subprocess
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Callable
from zoneinfo import ZoneInfo


BASE_DIR = Path(__file__).resolve().parents[1]
LOG_DIR = BASE_DIR / "logs"
TIMEZONE = ZoneInfo("Asia/Shanghai")

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class Job:
    name: str
    script: str
    args: list[str]
    lock_key: str
    env: dict[str, str] = field(default_factory=dict)


def execute_job(job_name: str, dry_run: bool = False) -> bool:
    succeeded = run_scheduled_jobs((job_name,), dry_run=dry_run)
    if not succeeded:
        raise RuntimeError(f"business job failed or overlapped: {job_name}")
    return True


def run_scheduled_jobs(
    job_names: tuple[str, ...] | list[str],
    dry_run: bool = False,
    extra_env: dict[str, str] | None = None,
    status_callback: Callable[[str, str], None] | None = None,
) -> bool:
    LOG_DIR.mkdir(parents=True, exist_ok=True)
    started_at = datetime.now(TIMEZONE)
    log_file = LOG_DIR / f"prefect_task_{started_at.strftime('%Y%m%d_%H%M%S')}.log"
    selected_jobs = build_jobs(job_names)
    succeeded = True
    with log_file.open("a", encoding="utf-8") as log_handle:
        write_log(log_handle, f"[TRIGGER] jobs={','.join(job_names)}")
        for job in selected_jobs:
            if dry_run:
                command = " ".join([job.script, *job.args])
                write_log(log_handle, f"[DRY-RUN] {job.name}: {command}")
                if status_callback:
                    status_callback(job.name, "dry-run")
                continue

            lock_file = lock_file_for(job)
            lock_fd = acquire_lock(lock_file)
            if lock_fd is None:
                succeeded = False
                write_log(
                    log_handle,
                    f"[SKIPPED-OVERLAP] {job.name}, lock={lock_file.name}",
                )
                logger.warning(
                    "status=skipped_overlap job=%s lock=%s",
                    job.name,
                    lock_file,
                )
                if status_callback:
                    status_callback(job.name, "overlapped")
                continue

            try:
                return_code = run_subprocess_job(
                    job,
                    log_handle,
                    runtime_env=extra_env,
                )
            finally:
                release_lock(lock_fd, lock_file)

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


def build_jobs(job_names: tuple[str, ...] | list[str]) -> list[Job]:
    scheduled_feature_limit = os.getenv("FEATURE_SCHEDULE_FUND_LIMIT", "2000")
    if not scheduled_feature_limit.isdigit() or int(scheduled_feature_limit) < 1:
        raise ValueError("FEATURE_SCHEDULE_FUND_LIMIT must be a positive integer")
    definitions = {
        "nav-performance": Job(
            name="nav-performance",
            script="./bin/run_nav_performance.sh",
            args=["--foreground"],
            lock_key="nav-performance",
        ),
        "feature": Job(
            name="feature",
            script="./bin/run_feature.sh",
            args=["--foreground"],
            lock_key="feature",
        ),
        "feature-scheduled": Job(
            name="feature-scheduled",
            script="./bin/run_feature.sh",
            args=[
                "--foreground",
                "--fund-limit",
                scheduled_feature_limit,
                "--stale-first",
                "1",
            ],
            lock_key="feature",
        ),
        "score": Job(
            name="score",
            script="./bin/run_score.sh",
            args=["--foreground"],
            lock_key="score",
        ),
        "sina-news": Job(
            name="sina-news",
            script="./bin/run_sina_news.sh",
            args=["--foreground"],
            lock_key="sina-news",
        ),
        "stock-cn": Job(
            name="stock-cn",
            script="./bin/run_stock.sh",
            args=["cn", "--foreground"],
            lock_key="stock-cn",
        ),
        "stock-hk": Job(
            name="stock-hk",
            script="./bin/run_stock.sh",
            args=["hk", "--foreground"],
            lock_key="stock-hk",
        ),
    }
    jobs: list[Job] = []
    for name in job_names:
        if name not in definitions:
            raise ValueError(f"unsupported business job: {name}")
        if all(existing.name != name for existing in jobs):
            jobs.append(definitions[name])
    return jobs


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


def lock_file_for(job: Job) -> Path:
    return LOG_DIR / f"crm_business_task_{job.lock_key}.lock"


def acquire_lock(lock_file: Path) -> int | None:
    for _ in range(3):
        try:
            lock_fd = os.open(str(lock_file), os.O_CREAT | os.O_EXCL | os.O_WRONLY)
            os.write(lock_fd, str(os.getpid()).encode("ascii"))
            return lock_fd
        except FileExistsError:
            owner_pid = read_lock_pid(lock_file)
            if owner_pid is not None and process_is_running(owner_pid):
                return None
            try:
                lock_file.unlink()
            except FileNotFoundError:
                continue
    return None


def read_lock_pid(lock_file: Path) -> int | None:
    try:
        value = lock_file.read_text(encoding="ascii").strip()
        return int(value) if value else None
    except (FileNotFoundError, OSError, ValueError):
        return None


def process_is_running(pid: int) -> bool:
    if pid <= 0:
        return False
    try:
        os.kill(pid, 0)
    except ProcessLookupError:
        return False
    except PermissionError:
        return True
    return True


def release_lock(lock_fd: int, lock_file: Path) -> None:
    os.close(lock_fd)
    try:
        lock_file.unlink()
    except FileNotFoundError:
        pass


def write_log(log_handle, message: str) -> None:
    line = f"{datetime.now(TIMEZONE):%Y-%m-%d %H:%M:%S%z} {message}"
    print(line, flush=True)
    log_handle.write(line + "\n")
    log_handle.flush()
