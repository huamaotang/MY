from __future__ import annotations

from datetime import datetime, timedelta, timezone

from prefect import flow, get_run_logger, task
from prefect.runtime import flow_run as runtime_flow_run

from runtime.task_runner import execute_job


@task(
    name="CRM business task",
    task_run_name="{job_name}",
    retries=0,
)
def run_business_job(job_name: str, dry_run: bool = False) -> bool:
    return execute_job(job_name, dry_run=dry_run)


@flow(name="fund-morning-refresh", log_prints=True)
def morning_fund_refresh_flow(dry_run: bool = False) -> None:
    """Refresh fund NAV/performance first, then derive feature data."""
    run_business_job("nav-performance", dry_run=dry_run)
    run_business_job("feature-scheduled", dry_run=dry_run)


@flow(name="fund-nav-performance", log_prints=True)
def nav_performance_flow(dry_run: bool = False) -> None:
    """Refresh current fund NAV and performance snapshots."""
    run_business_job("nav-performance", dry_run=dry_run)


@flow(name="fund-feature-refresh", log_prints=True)
def feature_refresh_flow(dry_run: bool = False) -> None:
    """Refresh fund feature data on demand."""
    run_business_job("feature", dry_run=dry_run)


@flow(name="fund-score-pipeline", log_prints=True)
def score_pipeline_flow(dry_run: bool = False) -> None:
    """Label snapshots, calculate scores, and process pending score jobs."""
    run_business_job("score", dry_run=dry_run)


@flow(name="sina-news-refresh", log_prints=True)
def sina_news_flow(dry_run: bool = False) -> None:
    """Refresh Sina finance news."""
    if scheduled_run_is_stale(max_lateness_seconds=300):
        get_run_logger().warning("Skipping stale Sina news run")
        return
    run_business_job("sina-news", dry_run=dry_run)


@flow(name="stock-cn-refresh", log_prints=True)
def stock_cn_flow(dry_run: bool = False) -> None:
    """Refresh mainland China stock quotes."""
    if scheduled_run_is_stale(max_lateness_seconds=600):
        get_run_logger().warning("Skipping stale mainland stock run")
        return
    run_business_job("stock-cn", dry_run=dry_run)


@flow(name="stock-hk-refresh", log_prints=True)
def stock_hk_flow(dry_run: bool = False) -> None:
    """Refresh Hong Kong stock quotes."""
    if scheduled_run_is_stale(max_lateness_seconds=600):
        get_run_logger().warning("Skipping stale Hong Kong stock run")
        return
    run_business_job("stock-hk", dry_run=dry_run)


def scheduled_run_is_stale(
    max_lateness_seconds: int,
    scheduled_start_time: datetime | None = None,
    now: datetime | None = None,
) -> bool:
    if max_lateness_seconds < 0:
        raise ValueError("max_lateness_seconds must not be negative")
    scheduled = scheduled_start_time or runtime_flow_run.scheduled_start_time
    reference = now or datetime.now(timezone.utc)
    if scheduled.tzinfo is None:
        scheduled = scheduled.replace(tzinfo=timezone.utc)
    if reference.tzinfo is None:
        reference = reference.replace(tzinfo=timezone.utc)
    return reference - scheduled > timedelta(seconds=max_lateness_seconds)
