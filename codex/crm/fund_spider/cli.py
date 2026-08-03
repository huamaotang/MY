from __future__ import annotations

import argparse
import logging
import os

import jobs
import scoring
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
    batch_parent.add_argument("--fund-code", "--fund_code", dest="FUND_CODE")
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
    nav_history.add_argument("--nav-page-workers", dest="NAV_PAGE_WORKERS")
    nav_history.add_argument("--nav-write-batch-size", dest="NAV_WRITE_BATCH_SIZE")
    nav_history.add_argument("--start-date", dest="NAV_START_DATE")
    nav_history.add_argument("--end-date", dest="NAV_END_DATE")

    feature = subparsers.add_parser(
        "feature",
        parents=[request_parent, db_parent, batch_parent],
        help="refresh feature data for selected funds",
    )
    feature.add_argument(
        "--stale-first",
        dest="FEATURE_STALE_FIRST",
        choices=["0", "1"],
        default=None,
        help="select the least recently refreshed funds; requires --fund-limit",
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

    score = subparsers.add_parser(
        "score",
        parents=[db_parent],
        help="calculate scores, run backtests, or process queued score jobs",
    )
    score.add_argument(
        "--mode",
        choices=["current", "history", "jobs", "backtest", "recommend", "pipeline"],
        default="current",
    )
    score.add_argument("--profile-id", type=int)
    score.add_argument("--job-limit", type=int, default=10)
    score.add_argument("--start-date", default="20180101")
    score.add_argument("--end-date")
    score.add_argument("--step-months", type=int, default=1)

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
        if args.LOG_SQL is None:
            os.environ["LOG_SQL"] = "1"
        if args.LOG_SQL_PARAMS is None:
            os.environ["LOG_SQL_PARAMS"] = "1"
        if args.LOG_SQL_MAX_PARAMS is None:
            os.environ["LOG_SQL_MAX_PARAMS"] = os.getenv("NAV_WRITE_BATCH_SIZE", "200")
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
    elif args.command == "score":
        if args.mode == "current":
            scoring.calculate_current_scores(args.profile_id)
        elif args.mode == "history":
            scoring.build_historical_factor_snapshots(
                args.start_date.replace("-", ""),
                None if args.end_date is None else args.end_date.replace("-", ""),
                args.step_months,
            )
        elif args.mode == "jobs":
            scoring.process_pending_jobs(limit=args.job_limit)
        elif args.mode == "backtest":
            if args.profile_id is None:
                raise ValueError("--profile-id is required for score --mode backtest")
            scoring.backtest_profile(args.profile_id)
        elif args.mode == "recommend":
            scoring.recommend_weights()
        else:
            scoring.label_matured_snapshots()
            scoring.calculate_current_scores(args.profile_id)
            scoring.process_pending_jobs(limit=args.job_limit)
    else:
        raise ValueError(f"unsupported command: {args.command}")


def ensure_no_failures(job_name: str, failed: int) -> None:
    if failed:
        raise RuntimeError(f"{job_name} completed with {failed} failed fund(s)")


if __name__ == "__main__":
    main()
