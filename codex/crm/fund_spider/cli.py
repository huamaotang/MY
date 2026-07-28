from __future__ import annotations

import argparse
import logging

import jobs
from runtime import scheduler, scheduler_web
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

    ranking_parent = argparse.ArgumentParser(add_help=False)
    ranking_parent.add_argument("--page-size", dest="PAGE_SIZE")
    ranking_parent.add_argument("--start-page", dest="START_PAGE")
    ranking_parent.add_argument("--max-pages", dest="MAX_PAGES")

    parser = argparse.ArgumentParser(description="EastMoney fund crawler")
    subparsers = parser.add_subparsers(dest="command", required=True)

    basic = subparsers.add_parser(
        "basic",
        parents=[request_parent, db_parent, batch_parent, ranking_parent],
        help="manually refresh fund summaries and basic profiles",
    )
    basic.add_argument(
        "--refresh-list",
        dest="BASIC_REFRESH_LIST",
        choices=["0", "1"],
        default=None,
        help="refresh fund code/name/purchase status before profiles",
    )

    subparsers.add_parser(
        "nav-performance",
        parents=[request_parent, db_parent, ranking_parent],
        help="refresh current NAV and performance snapshots",
    )

    nav_history = subparsers.add_parser(
        "nav-history",
        parents=[request_parent, db_parent, batch_parent],
        help="manually crawl historical NAV for selected funds",
    )
    nav_history.add_argument("--nav-page-size", dest="NAV_PAGE_SIZE")
    nav_history.add_argument("--nav-start-page", dest="NAV_START_PAGE")
    nav_history.add_argument("--nav-max-pages", dest="NAV_MAX_PAGES")
    nav_history.add_argument("--start-date", dest="NAV_START_DATE")
    nav_history.add_argument("--end-date", dest="NAV_END_DATE")

    subparsers.add_parser(
        "feature",
        parents=[request_parent, db_parent, batch_parent],
        help="refresh feature data for selected funds",
    )

    rating = subparsers.add_parser(
        "rating",
        parents=[request_parent, db_parent, batch_parent],
        help="manually refresh current or historical ratings",
    )
    rating.add_argument("--mode", choices=["current", "history"], default="current")
    rating.add_argument("--rating-page-size", dest="RATING_PAGE_SIZE")
    rating.add_argument("--rating-max-pages", dest="RATING_MAX_PAGES")

    holdings = subparsers.add_parser(
        "holdings",
        parents=[request_parent, db_parent, batch_parent],
        help="manually refresh fund holdings",
    )
    holdings.add_argument("--top-line", dest="HOLDING_TOP_LINE")
    holdings.add_argument("--year", dest="HOLDING_YEAR")
    holdings.add_argument("--month", dest="HOLDING_MONTH")

    news = subparsers.add_parser("news", parents=[request_parent, db_parent])
    news.add_argument("--score", dest="YJB_NEWS_SCORE")
    sina_news = subparsers.add_parser("sina-news", parents=[request_parent, db_parent])
    sina_news.add_argument("--max-pages", dest="SINA_NEWS_MAX_PAGES")
    stock = subparsers.add_parser("stock", parents=[request_parent, db_parent])
    stock.add_argument("--page-size", dest="STOCK_PAGE_SIZE")
    stock.add_argument("--market", dest="STOCK_MARKET", choices=["cn", "hk", "all"], default=None)

    schedule = subparsers.add_parser("schedule")
    schedule.add_argument("--nav-times", dest="NAV_PERFORMANCE_SCHEDULE_TIMES")
    schedule.add_argument("--feature-time", dest="FEATURE_SCHEDULE_TIME")
    schedule.add_argument("--run-on-start", dest="SCHEDULER_RUN_ON_START", choices=["0", "1"], default=None)
    schedule.add_argument("--once", dest="SCHEDULER_ONCE", choices=["0", "1"], default=None)
    schedule.add_argument("--dry-run", dest="SCHEDULER_DRY_RUN", choices=["0", "1"], default=None)
    schedule.add_argument(
        "--trigger",
        dest="SCHEDULER_TRIGGER",
        choices=["morning", "evening", "all"],
        default=None,
    )

    web = subparsers.add_parser("web")
    web.add_argument("--host", dest="SCHEDULER_WEB_HOST")
    web.add_argument("--port", dest="SCHEDULER_WEB_PORT")

    return parser


def build_env_overrides(args: argparse.Namespace) -> dict[str, str | None]:
    return {
        key: None if value is None else str(value)
        for key, value in vars(args).items()
        if key.isupper()
    }


def run_command(args: argparse.Namespace) -> None:
    if args.command == "basic":
        result = jobs.crawl_basic()
        ensure_no_failures("basic", result[2])
    elif args.command == "nav-performance":
        jobs.crawl_nav_performance()
    elif args.command == "nav-history":
        result = jobs.crawl_nav_history()
        ensure_no_failures("nav-history", result[1])
    elif args.command == "feature":
        result = jobs.crawl_feature_data()
        ensure_no_failures("feature", result[1])
    elif args.command == "rating":
        if args.mode == "current":
            jobs.crawl_rating_list()
        else:
            result = jobs.crawl_ratings()
            ensure_no_failures("rating history", result[1])
    elif args.command == "holdings":
        result = jobs.crawl_holdings()
        ensure_no_failures("holdings", result[1])
    elif args.command == "news":
        jobs.crawl_yangjibao_news()
    elif args.command == "sina-news":
        jobs.crawl_sina_news()
    elif args.command == "stock":
        jobs.crawl_stocks()
    elif args.command == "schedule":
        scheduler.main()
    elif args.command == "web":
        scheduler_web.main()
    else:
        raise ValueError(f"unsupported command: {args.command}")


def ensure_no_failures(job_name: str, failed: int) -> None:
    if failed:
        raise RuntimeError(f"{job_name} completed with {failed} failed fund(s)")


if __name__ == "__main__":
    main()
