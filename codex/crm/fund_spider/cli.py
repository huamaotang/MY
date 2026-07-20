from __future__ import annotations

import argparse
import logging
from typing import Any

import jobs
import scheduler
import scheduler_web
from settings import apply_env_overrides, load_project_env


def main(argv: list[str] | None = None) -> None:
    load_project_env()
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s - %(message)s",
    )

    parser = build_parser()
    args = parser.parse_args(argv)
    apply_env_overrides(build_env_overrides(args))
    run_command(args)


def build_parser() -> argparse.ArgumentParser:
    request_parent = argparse.ArgumentParser(add_help=False)
    request_parent.add_argument("--request-min-delay", dest="REQUEST_MIN_DELAY_SECONDS")
    request_parent.add_argument("--request-max-delay", dest="REQUEST_MAX_DELAY_SECONDS")
    request_parent.add_argument("--request-timeout", dest="REQUEST_TIMEOUT_SECONDS")
    request_parent.add_argument("--request-retries", dest="REQUEST_MAX_RETRIES")
    request_parent.add_argument("--log-sql", dest="LOG_SQL", choices=["0", "1"], default=None)
    request_parent.add_argument("--log-sql-params", dest="LOG_SQL_PARAMS", choices=["0", "1"], default=None)
    request_parent.add_argument("--log-sql-max-params", dest="LOG_SQL_MAX_PARAMS")

    db_parent = argparse.ArgumentParser(add_help=False)
    db_parent.add_argument("--db-host", dest="DB_HOST")
    db_parent.add_argument("--db-port", dest="DB_PORT")
    db_parent.add_argument("--db-user", dest="DB_USER")
    db_parent.add_argument("--db-password", dest="DB_PASSWORD")
    db_parent.add_argument("--db-name", dest="DB_NAME")

    batch_parent = argparse.ArgumentParser(add_help=False)
    batch_parent.add_argument("--fund-code", dest="FUND_CODE")
    batch_parent.add_argument("--fund-start-code", dest="FUND_START_CODE")
    batch_parent.add_argument("--fund-limit", dest="FUND_LIMIT")
    batch_parent.add_argument("--fund-offset", dest="FUND_OFFSET")

    nav_parent = argparse.ArgumentParser(add_help=False)
    nav_parent.add_argument("--nav-page-size", dest="NAV_PAGE_SIZE")
    nav_parent.add_argument("--nav-start-page", dest="NAV_START_PAGE")
    nav_parent.add_argument("--nav-max-pages", dest="NAV_MAX_PAGES")
    nav_parent.add_argument("--nav-start-date", dest="NAV_START_DATE")
    nav_parent.add_argument("--nav-end-date", dest="NAV_END_DATE")

    parser = argparse.ArgumentParser(description="EastMoney fund crawler")
    subparsers = parser.add_subparsers(dest="command", required=True)

    fund_list = subparsers.add_parser("fund-list", parents=[request_parent, db_parent])
    fund_list.add_argument("--page-size", dest="PAGE_SIZE")
    fund_list.add_argument("--start-page", dest="START_PAGE")
    fund_list.add_argument("--max-pages", dest="MAX_PAGES")

    nav = subparsers.add_parser("nav", parents=[request_parent, db_parent, nav_parent])
    nav.add_argument("--fund-code", dest="NAV_FUND_CODE")

    profile_nav = subparsers.add_parser("profile-nav", parents=[request_parent, db_parent, batch_parent, nav_parent])
    profile_nav.add_argument("--crawl-profile", dest="CRAWL_PROFILE", choices=["0", "1"], default=None)
    profile_nav.add_argument("--crawl-nav", dest="CRAWL_NAV", choices=["0", "1"], default=None)

    feature = subparsers.add_parser("feature", parents=[request_parent, db_parent, batch_parent])
    feature.add_argument("--feature-fund-code", dest="FEATURE_FUND_CODE")

    holdings = subparsers.add_parser("holdings", parents=[request_parent, db_parent, batch_parent])
    holdings.add_argument("--holding-fund-code", dest="HOLDING_FUND_CODE")
    holdings.add_argument("--top-line", dest="HOLDING_TOP_LINE")
    holdings.add_argument("--year", dest="HOLDING_YEAR")
    holdings.add_argument("--month", dest="HOLDING_MONTH")

    all_jobs = subparsers.add_parser("all", parents=[request_parent, db_parent, batch_parent, nav_parent])
    all_jobs.add_argument(
        "--jobs",
        default="fund-list,profile-nav,feature,holdings",
        help="Comma-separated jobs: fund-list,profile-nav,feature,holdings",
    )
    all_jobs.add_argument("--crawl-profile", dest="CRAWL_PROFILE", choices=["0", "1"], default=None)
    all_jobs.add_argument("--crawl-nav", dest="CRAWL_NAV", choices=["0", "1"], default=None)
    all_jobs.add_argument("--top-line", dest="HOLDING_TOP_LINE")
    all_jobs.add_argument("--year", dest="HOLDING_YEAR")
    all_jobs.add_argument("--month", dest="HOLDING_MONTH")
    all_jobs.add_argument("--page-size", dest="PAGE_SIZE")
    all_jobs.add_argument("--start-page", dest="START_PAGE")
    all_jobs.add_argument("--max-pages", dest="MAX_PAGES")

    daily = subparsers.add_parser("daily", parents=[request_parent, db_parent, batch_parent, nav_parent])
    daily.add_argument("--refresh-fund-list", dest="DAILY_CRAWL_FUND_LIST", choices=["0", "1"], default=None)
    daily.add_argument("--crawl-profile", dest="DAILY_CRAWL_PROFILE", choices=["0", "1"], default=None)
    daily.add_argument("--crawl-nav", dest="DAILY_CRAWL_NAV", choices=["0", "1"], default=None)
    daily.add_argument("--crawl-feature", dest="DAILY_CRAWL_FEATURE", choices=["0", "1"], default=None)
    daily.add_argument("--crawl-holdings", dest="DAILY_CRAWL_HOLDINGS", choices=["0", "1"], default=None)
    daily.add_argument("--use-cursor", dest="DAILY_USE_CURSOR", choices=["0", "1"], default=None)
    daily.add_argument("--cursor-date", dest="DAILY_CURSOR_DATE")
    daily.add_argument("--cursor-job-name", dest="DAILY_CURSOR_JOB_NAME")
    daily.add_argument("--top-line", dest="HOLDING_TOP_LINE")
    daily.add_argument("--year", dest="HOLDING_YEAR")
    daily.add_argument("--month", dest="HOLDING_MONTH")
    daily.add_argument("--page-size", dest="PAGE_SIZE")
    daily.add_argument("--start-page", dest="START_PAGE")
    daily.add_argument("--max-pages", dest="MAX_PAGES")

    schedule = subparsers.add_parser("schedule")
    schedule.add_argument("--time", dest="SCHEDULE_TIME")
    schedule.add_argument("--run-on-start", dest="SCHEDULER_RUN_ON_START", choices=["0", "1"], default=None)
    schedule.add_argument("--once", dest="SCHEDULER_ONCE", choices=["0", "1"], default=None)
    schedule.add_argument("--dry-run", dest="SCHEDULER_DRY_RUN", choices=["0", "1"], default=None)

    web = subparsers.add_parser("web")
    web.add_argument("--host", dest="SCHEDULER_WEB_HOST")
    web.add_argument("--port", dest="SCHEDULER_WEB_PORT")

    return parser


def build_env_overrides(args: argparse.Namespace) -> dict[str, str | None]:
    overrides: dict[str, str | None] = {}
    for key, value in vars(args).items():
        if key.isupper():
            overrides[key] = None if value is None else str(value)

    if getattr(args, "FEATURE_FUND_CODE", None):
        overrides["FUND_CODE"] = args.FEATURE_FUND_CODE
    if getattr(args, "HOLDING_FUND_CODE", None):
        overrides["FUND_CODE"] = args.HOLDING_FUND_CODE
    return overrides


def run_command(args: argparse.Namespace) -> None:
    command = args.command
    if command == "fund-list":
        jobs.crawl_fund_list()
    elif command == "nav":
        jobs.crawl_nav()
    elif command == "profile-nav":
        jobs.crawl_profile_nav()
    elif command == "feature":
        jobs.crawl_feature_data()
    elif command == "holdings":
        jobs.crawl_holdings()
    elif command == "all":
        run_job_chain(args.jobs)
    elif command == "daily":
        jobs.crawl_daily_update()
    elif command == "schedule":
        scheduler.main()
    elif command == "web":
        scheduler_web.main()
    else:
        raise ValueError(f"unsupported command: {command}")


def run_job_chain(job_names: str) -> None:
    dispatch: dict[str, Any] = {
        "fund-list": jobs.crawl_fund_list,
        "profile-nav": jobs.crawl_profile_nav,
        "feature": jobs.crawl_feature_data,
        "holdings": jobs.crawl_holdings,
    }
    for raw_name in job_names.split(","):
        name = raw_name.strip()
        if not name:
            continue
        if name not in dispatch:
            raise ValueError(f"unsupported job: {name}")
        dispatch[name]()


if __name__ == "__main__":
    main()
