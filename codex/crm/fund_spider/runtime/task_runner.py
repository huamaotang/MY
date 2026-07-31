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
LOCK_FILE = LOG_DIR / "crm_business_task.lock"
TIMEZONE = ZoneInfo("Asia/Shanghai")

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class Job:
    name: str
    script: str
    args: list[str]
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


def acquire_lock() -> int | None:
    try:
        lock_fd = os.open(str(LOCK_FILE), os.O_CREAT | os.O_EXCL | os.O_WRONLY)
        os.write(lock_fd, str(os.getpid()).encode("ascii"))
        return lock_fd
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
