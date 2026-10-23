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
FAST_FLOW_NAMES: tuple[str, ...] = (*REALTIME_FLOW_NAMES, "crm-ops-janitor")
BATCH_FLOW_NAMES: tuple[str, ...] = (
    "fund-morning-refresh",
    "fund-nav-performance",
    "fund-score-pipeline",
)
DEFAULT_STALE_RUN_MINUTES = 30
DEFAULT_BATCH_STALE_RUN_MINUTES = 480
DEFAULT_ORPHAN_SLOT_IDLE_MINUTES = 10
STALE_LATENESS_SECONDS = {
    "sina-news-refresh": 300,
    "stock-cn-refresh": 3600,
    "stock-hk-refresh": 3600,
}


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
    if scheduled_run_is_stale(max_lateness_seconds=STALE_LATENESS_SECONDS["stock-cn-refresh"]):
        get_run_logger().warning("Skipping stale mainland stock run")
        return
    run_business_job("stock-cn", dry_run=dry_run)


@flow(name="stock-hk-refresh", log_prints=True)
def stock_hk_flow(dry_run: bool = False) -> None:
    """Refresh Hong Kong stock quotes."""
    if scheduled_run_is_stale(max_lateness_seconds=STALE_LATENESS_SECONDS["stock-hk-refresh"]):
        get_run_logger().warning("Skipping stale Hong Kong stock run")
        return
    run_business_job("stock-hk", dry_run=dry_run)


@flow(name="crm-ops-janitor", log_prints=True)
def ops_janitor_flow(
    dry_run: bool = False,
    max_age_minutes: int = DEFAULT_STALE_RUN_MINUTES,
    batch_max_age_minutes: int = DEFAULT_BATCH_STALE_RUN_MINUTES,
) -> list[str]:
    """Finalize stuck realtime and scheduled batch runs so slots are released."""
    return reap_stale_flow_runs(
        max_age_minutes=max_age_minutes,
        batch_max_age_minutes=batch_max_age_minutes,
        dry_run=dry_run,
    )


@task(name="reap-stale-flow-runs", task_run_name="reap-stale-runs", retries=0)
def reap_stale_flow_runs(
    max_age_minutes: int = DEFAULT_STALE_RUN_MINUTES,
    batch_max_age_minutes: int = DEFAULT_BATCH_STALE_RUN_MINUTES,
    dry_run: bool = False,
) -> list[str]:
    with get_client(sync_client=True) as client:
        reaped = reap_stale_running_runs(
            client,
            max_age_minutes=max_age_minutes,
            dry_run=dry_run,
            flow_names=FAST_FLOW_NAMES,
        )
        reaped.extend(
            reap_stale_running_runs(
                client,
                max_age_minutes=batch_max_age_minutes,
                dry_run=dry_run,
                flow_names=BATCH_FLOW_NAMES,
            )
        )
        release_orphaned_deployment_slots(
            client,
            idle_minutes=DEFAULT_ORPHAN_SLOT_IDLE_MINUTES,
            dry_run=dry_run,
        )
        return reaped


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
    reaped: list[str] = []
    state_checks = (
        (StateType.RUNNING, "start_time"),
        (StateType.PENDING, "expected_start_time"),
    )
    for state_type, time_field in state_checks:
        stale_runs = client.read_flow_runs(
            flow_filter=FlowFilter(name={"any_": list(flow_names or FAST_FLOW_NAMES)}),
            flow_run_filter=FlowRunFilter(
                state={"type": {"any_": [state_type]}},
                **{time_field: {"before_": cutoff}},
            ),
        )
        for run in stale_runs:
            since = run.start_time or run.expected_start_time
            logger.warning(
                "status=reap_stale_run flow_run=%s name=%s state=%s",
                run.id,
                run.name,
                state_type.name,
            )
            if dry_run:
                continue
            try:
                client.set_flow_run_state(
                    run.id,
                    Failed(
                        message=(
                            "finalized by crm-ops-janitor: "
                            f"{state_type.name.lower()} since {since}"
                        )
                    ),
                    force=True,
                )
            except Exception as exc:
                logger.error("status=reap_failed flow_run=%s error=%s", run.id, exc)
                continue
            reaped.append(str(run.id))
    return reaped


def release_orphaned_deployment_slots(
    client,
    idle_minutes: int = DEFAULT_ORPHAN_SLOT_IDLE_MINUTES,
    dry_run: bool = False,
    now: datetime | None = None,
) -> list[str]:
    if idle_minutes <= 0:
        raise ValueError("idle_minutes must be positive")
    reference = now or datetime.now(timezone.utc)
    if reference.tzinfo is None:
        reference = reference.replace(tzinfo=timezone.utc)
    released: list[str] = []
    for deployment in client.read_deployments():
        limit = getattr(deployment, "global_concurrency_limit", None)
        if limit is None or not limit.active_slots:
            continue
        limit_updated = limit.updated
        if limit_updated.tzinfo is None:
            limit_updated = limit_updated.replace(tzinfo=timezone.utc)
        idle_seconds = (reference - limit_updated).total_seconds()
        if idle_seconds < idle_minutes * 60:
            continue
        runs = client.read_flow_runs(
            flow_run_filter=FlowRunFilter(
                deployment_id={"any_": [deployment.id]},
                state={"type": {"any_": [StateType.RUNNING, StateType.PENDING]}},
            ),
            limit=200,
        )
        holding_slots = sum(1 for run in runs if run.state_name in ("Running", "Submitting"))
        orphaned = limit.active_slots - holding_slots
        if orphaned <= 0:
            continue
        logger.warning(
            "status=release_orphaned_slots deployment=%s slots=%s active=%s holding=%s",
            deployment.name,
            orphaned,
            limit.active_slots,
            holding_slots,
        )
        if dry_run:
            continue
        try:
            client.release_concurrency_slots(
                names=[limit.name],
                slots=orphaned,
                occupancy_seconds=max(1.0, idle_seconds),
            )
        except Exception as exc:
            logger.error(
                "status=release_orphaned_slots_failed deployment=%s error=%s",
                deployment.name,
                exc,
            )
            continue
        released.append(limit.name)
    return released


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
