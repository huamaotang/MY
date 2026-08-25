from __future__ import annotations

import logging
from datetime import datetime, timedelta, timezone

from prefect import flow, get_run_logger, task
from prefect.client.orchestration import get_client
from prefect.client.schemas.filters import FlowFilter, FlowRunFilter
from prefect.client.schemas.objects import StateType
from prefect.runtime import flow_run as runtime_flow_run
from prefect.states import Failed

from runtime.task_runner import execute_job


logger = logging.getLogger(__name__)

REALTIME_FLOW_NAMES: tuple[str, ...] = (
    "sina-news-refresh",
    "stock-cn-refresh",
    "stock-hk-refresh",
)
DEFAULT_STALE_RUN_MINUTES = 30


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


@flow(name="crm-ops-janitor", log_prints=True)
def ops_janitor_flow(
    dry_run: bool = False,
    max_age_minutes: int = DEFAULT_STALE_RUN_MINUTES,
) -> list[str]:
    """Finalize stuck realtime runs so concurrency slots are released."""
    return reap_stale_flow_runs(max_age_minutes=max_age_minutes, dry_run=dry_run)


@task(name="reap-stale-flow-runs", task_run_name="reap-stale-runs", retries=0)
def reap_stale_flow_runs(
    max_age_minutes: int = DEFAULT_STALE_RUN_MINUTES,
    dry_run: bool = False,
) -> list[str]:
    with get_client(sync_client=True) as client:
        return reap_stale_running_runs(
            client,
            max_age_minutes=max_age_minutes,
            dry_run=dry_run,
        )


def reap_stale_running_runs(
    client,
    max_age_minutes: int = DEFAULT_STALE_RUN_MINUTES,
    dry_run: bool = False,
    flow_names: tuple[str, ...] | None = None,
    now: datetime | None = None,
) -> list[str]:
    if max_age_minutes <= 0:
        raise ValueError("max_age_minutes must be positive")
    cutoff = (now or datetime.now(timezone.utc)) - timedelta(minutes=max_age_minutes)
    if cutoff.tzinfo is None:
        cutoff = cutoff.replace(tzinfo=timezone.utc)
    stale_runs = client.read_flow_runs(
        flow_filter=FlowFilter(name={"any_": list(flow_names or REALTIME_FLOW_NAMES)}),
        flow_run_filter=FlowRunFilter(
            state={"type": {"any_": [StateType.RUNNING]}},
            expected_start_time={"before_": cutoff},
        ),
    )
    reaped: list[str] = []
    for run in stale_runs:
        logger.warning("status=reap_stale_run flow_run=%s name=%s", run.id, run.name)
        if dry_run:
            continue
        try:
            client.set_flow_run_state(
                run.id,
                Failed(message=f"finalized by crm-ops-janitor: running since {run.expected_start_time}"),
                force=True,
            )
        except Exception as exc:
            logger.error("status=reap_failed flow_run=%s error=%s", run.id, exc)
            continue
        reaped.append(str(run.id))
    return reaped


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
