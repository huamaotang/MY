from __future__ import annotations

from prefect import flow, task

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
    run_business_job("feature", dry_run=dry_run)


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
    run_business_job("sina-news", dry_run=dry_run)


@flow(name="stock-cn-refresh", log_prints=True)
def stock_cn_flow(dry_run: bool = False) -> None:
    """Refresh mainland China stock quotes."""
    run_business_job("stock-cn", dry_run=dry_run)


@flow(name="stock-hk-refresh", log_prints=True)
def stock_hk_flow(dry_run: bool = False) -> None:
    """Refresh Hong Kong stock quotes."""
    run_business_job("stock-hk", dry_run=dry_run)
