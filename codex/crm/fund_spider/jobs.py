from __future__ import annotations

import logging
import os
from dataclasses import dataclass, field
from datetime import datetime

from db import (
    DatabaseConfig,
    connect,
    database_config_from_env,
    ensure_schema,
    fund_profile_recently_updated,
    fund_data_recently_refreshed,
    fund_table_recently_updated,
    get_crawl_cursor,
    list_fund_codes,
    update_fund_profile,
    upsert_crawl_cursor,
    upsert_feature_data,
    upsert_funds,
    upsert_fund_ratings,
    upsert_fund_refresh_state,
    upsert_nav_history,
    upsert_stock_holdings,
)
from feature_spider import EastMoneyFeatureSpider
from holding_spider import EastMoneyHoldingSpider
from nav_spider import EastMoneyNavSpider
from profile_spider import EastMoneyProfileSpider
from rating_spider import EastMoneyRatingSpider
from settings import normalize_query_date, parse_bool, parse_optional_int, request_config_from_env
from spider import EastMoneyFundSpider, RequestConfig


logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class BatchSelector:
    fund_code: str = ""
    fund_start_code: str = ""
    fund_limit: int | None = None
    fund_offset: int = 0


@dataclass(frozen=True)
class FundListOptions:
    page_size: int = 200
    start_page: int = 1
    max_pages: int | None = None


@dataclass(frozen=True)
class NavOptions:
    fund_code: str = "519674"
    page_size: int = 20
    start_page: int = 1
    max_pages: int | None = None
    start_date: str = ""
    end_date: str = ""


@dataclass(frozen=True)
class ProfileNavOptions:
    selector: BatchSelector
    crawl_profile: bool = True
    crawl_nav: bool = True
    nav_page_size: int = 20
    nav_start_page: int = 1
    nav_max_pages: int | None = None
    nav_start_date: str = ""
    nav_end_date: str = ""


@dataclass(frozen=True)
class FeatureOptions:
    selector: BatchSelector


@dataclass(frozen=True)
class RatingOptions:
    selector: BatchSelector
    page_size: int = 50
    max_pages: int | None = None


@dataclass(frozen=True)
class HoldingOptions:
    selector: BatchSelector
    top_line: int = 10
    year: str = ""
    month: str = ""


@dataclass(frozen=True)
class DailyUpdateOptions:
    selector: BatchSelector
    refresh_fund_list: bool = True
    crawl_profile: bool = True
    crawl_nav: bool = True
    crawl_feature: bool = True
    crawl_rating: bool = True
    crawl_holdings: bool = True
    fund_list_options: FundListOptions = field(default_factory=FundListOptions)
    nav_page_size: int = 20
    nav_start_page: int = 1
    nav_max_pages: int | None = None
    nav_start_date: str = ""
    nav_end_date: str = ""
    nav_refresh_days: int = 1
    profile_refresh_days: int = 30
    feature_refresh_days: int = 7
    rating_refresh_days: int = 7
    holding_refresh_days: int = 7
    holding_top_line: int = 10
    rating_page_size: int = 50
    rating_max_pages: int | None = 1
    holding_year: str = ""
    holding_month: str = ""
    use_cursor: bool = True
    cursor_date: str = ""
    cursor_job_name: str = "daily_update"


def crawl_fund_list(
    options: FundListOptions | None = None,
    db_config: DatabaseConfig | None = None,
    request_config: RequestConfig | None = None,
) -> int:
    options = options or fund_list_options_from_env()
    db_config = db_config or database_config_from_env()
    request_config = request_config or request_config_from_env()

    ensure_schema(db_config)
    spider = EastMoneyFundSpider(request_config)
    connection = connect(db_config)
    total_parsed = 0
    total_saved = 0
    try:
        for page in spider.iter_pages(
            start_page=options.start_page,
            page_size=options.page_size,
            max_pages=options.max_pages,
        ):
            total_parsed += len(page.funds)
            saved = upsert_funds(connection, page.funds)
            total_saved += saved
            codes = [fund_code for fund_code, _ in page.funds]
            logger.info(
                "step=fund_list page=%s/%s parsed=%s saved=%s total_records=%s first_code=%s last_code=%s sample_codes=%s",
                page.page_index,
                page.total_pages,
                len(page.funds),
                saved,
                page.total_records,
                codes[0] if codes else "-",
                codes[-1] if codes else "-",
                ",".join(codes[:5]) if codes else "-",
            )
    finally:
        connection.close()

    logger.info("step=fund_list status=completed parsed=%s saved=%s", total_parsed, total_saved)
    return total_saved


def crawl_nav(
    options: NavOptions | None = None,
    db_config: DatabaseConfig | None = None,
    request_config: RequestConfig | None = None,
) -> int:
    options = options or nav_options_from_env()
    db_config = db_config or database_config_from_env()
    request_config = request_config or request_config_from_env()

    ensure_schema(db_config)
    spider = EastMoneyNavSpider(request_config)
    connection = connect(db_config)
    total_saved = 0
    try:
        for page in spider.iter_pages(
            fund_code=options.fund_code,
            page_size=options.page_size,
            start_page=options.start_page,
            max_pages=options.max_pages,
            start_date=options.start_date,
            end_date=options.end_date,
        ):
            saved = upsert_nav_history(connection, page.rows)
            total_saved += saved
            logger.info(
                "fund %s nav page %s/%s parsed %s rows, saved rows: %s, total count: %s",
                page.fund_code,
                page.page_index,
                page.total_pages,
                len(page.rows),
                saved,
                page.total_count,
            )
    finally:
        connection.close()

    logger.info("nav crawl completed, fund %s saved rows: %s", options.fund_code, total_saved)
    return total_saved


def crawl_profile_nav(
    options: ProfileNavOptions | None = None,
    db_config: DatabaseConfig | None = None,
    request_config: RequestConfig | None = None,
) -> tuple[int, int]:
    options = options or profile_nav_options_from_env()
    db_config = db_config or database_config_from_env()
    request_config = request_config or request_config_from_env()

    ensure_schema(db_config)
    fund_codes = load_selected_fund_codes(db_config, options.selector)
    logger.info("loaded %s fund codes from cfg_fund", len(fund_codes))

    profile_spider = EastMoneyProfileSpider(request_config)
    nav_spider = EastMoneyNavSpider(request_config)
    succeeded = 0
    failed = 0

    for index, fund_code in enumerate(fund_codes, start=1):
        logger.info("processing fund %s (%s/%s)", fund_code, index, len(fund_codes))
        connection = connect(db_config)
        try:
            if options.crawl_profile:
                profile = profile_spider.fetch_profile(fund_code)
                update_fund_profile(connection, profile)
                logger.info(
                    "fund %s profile saved: inception=%s manager=%s type=%s company=%s scale=%s scale_date=%s",
                    fund_code,
                    profile.inception_date,
                    profile.fund_manager,
                    profile.fund_type,
                    profile.management_company,
                    profile.net_asset_scale,
                    profile.scale_date,
                )

            if options.crawl_nav:
                total_nav_rows = 0
                for page in nav_spider.iter_pages(
                    fund_code=fund_code,
                    page_size=options.nav_page_size,
                    start_page=options.nav_start_page,
                    max_pages=options.nav_max_pages,
                    start_date=options.nav_start_date,
                    end_date=options.nav_end_date,
                ):
                    saved = upsert_nav_history(connection, page.rows)
                    total_nav_rows += saved
                    logger.info(
                        "fund %s nav page %s/%s parsed %s rows, saved rows: %s, total count: %s",
                        fund_code,
                        page.page_index,
                        page.total_pages,
                        len(page.rows),
                        saved,
                        page.total_count,
                    )
                logger.info("fund %s nav saved rows: %s", fund_code, total_nav_rows)

            succeeded += 1
        except Exception:
            failed += 1
            logger.exception("fund %s failed", fund_code)
        finally:
            connection.close()

    logger.info("profile/nav crawl completed, succeeded: %s, failed: %s", succeeded, failed)
    return succeeded, failed


def crawl_feature_data(
    options: FeatureOptions | None = None,
    db_config: DatabaseConfig | None = None,
    request_config: RequestConfig | None = None,
) -> tuple[int, int, int]:
    options = options or feature_options_from_env()
    db_config = db_config or database_config_from_env()
    request_config = request_config or request_config_from_env()

    ensure_schema(db_config)
    fund_codes = load_selected_fund_codes(db_config, options.selector)
    logger.info("loaded %s fund codes for feature data", len(fund_codes))

    spider = EastMoneyFeatureSpider(request_config)
    succeeded = 0
    failed = 0
    total_saved = 0
    connection = connect(db_config)
    try:
        for index, fund_code in enumerate(fund_codes, start=1):
            try:
                rows = spider.fetch_feature_data(fund_code)
                saved = upsert_feature_data(connection, rows)
                total_saved += saved
                succeeded += 1
                logger.info("fund %s (%s/%s) feature rows saved: %s", fund_code, index, len(fund_codes), saved)
            except Exception:
                failed += 1
                logger.exception("fund %s feature data failed", fund_code)
    finally:
        connection.close()

    logger.info("feature crawl completed, succeeded: %s, failed: %s, saved rows: %s", succeeded, failed, total_saved)
    return succeeded, failed, total_saved


def crawl_ratings(
    options: RatingOptions | None = None,
    db_config: DatabaseConfig | None = None,
    request_config: RequestConfig | None = None,
) -> tuple[int, int, int]:
    options = options or rating_options_from_env()
    db_config = db_config or database_config_from_env()
    request_config = request_config or request_config_from_env()

    ensure_schema(db_config)
    fund_codes = load_selected_fund_codes(db_config, options.selector)
    logger.info("loaded %s fund codes for ratings", len(fund_codes))

    spider = EastMoneyRatingSpider(request_config)
    succeeded = 0
    failed = 0
    total_saved = 0
    connection = connect(db_config)
    try:
        for index, fund_code in enumerate(fund_codes, start=1):
            try:
                rows = spider.fetch_ratings(fund_code, page_size=options.page_size, max_pages=options.max_pages)
                saved = upsert_fund_ratings(connection, rows)
                total_saved += saved
                succeeded += 1
                logger.info(
                    "fund %s (%s/%s) rating rows parsed: %s, saved: %s",
                    fund_code,
                    index,
                    len(fund_codes),
                    len(rows),
                    saved,
                )
            except Exception:
                failed += 1
                logger.exception("fund %s rating data failed", fund_code)
    finally:
        connection.close()

    logger.info("rating crawl completed, succeeded: %s, failed: %s, saved rows: %s", succeeded, failed, total_saved)
    return succeeded, failed, total_saved


def crawl_holdings(
    options: HoldingOptions | None = None,
    db_config: DatabaseConfig | None = None,
    request_config: RequestConfig | None = None,
) -> tuple[int, int, int]:
    options = options or holding_options_from_env()
    db_config = db_config or database_config_from_env()
    request_config = request_config or request_config_from_env()

    ensure_schema(db_config)
    fund_codes = load_selected_fund_codes(db_config, options.selector)
    logger.info("loaded %s fund codes for holdings", len(fund_codes))

    spider = EastMoneyHoldingSpider(request_config)
    succeeded = 0
    failed = 0
    total_saved = 0
    connection = connect(db_config)
    try:
        for index, fund_code in enumerate(fund_codes, start=1):
            try:
                rows = spider.fetch_holdings(
                    fund_code,
                    top_line=options.top_line,
                    year=options.year,
                    month=options.month,
                )
                saved = upsert_stock_holdings(connection, rows)
                total_saved += saved
                succeeded += 1
                logger.info(
                    "fund %s (%s/%s) holding rows parsed: %s, saved: %s",
                    fund_code,
                    index,
                    len(fund_codes),
                    len(rows),
                    saved,
                )
            except Exception:
                failed += 1
                logger.exception("fund %s holding data failed", fund_code)
    finally:
        connection.close()

    logger.info("holding crawl completed, succeeded: %s, failed: %s, saved rows: %s", succeeded, failed, total_saved)
    return succeeded, failed, total_saved


def crawl_daily_update(
    options: DailyUpdateOptions | None = None,
    db_config: DatabaseConfig | None = None,
    request_config: RequestConfig | None = None,
) -> tuple[int, int]:
    options = options or daily_update_options_from_env()
    db_config = db_config or database_config_from_env()
    request_config = request_config or request_config_from_env()

    ensure_schema(db_config)
    if options.refresh_fund_list:
        logger.info(
            "daily update step=fund_list status=start page_size=%s start_page=%s max_pages=%s",
            options.fund_list_options.page_size,
            options.fund_list_options.start_page,
            options.fund_list_options.max_pages,
        )
        crawl_fund_list(options.fund_list_options, db_config, request_config)
        logger.info("daily update step=fund_list status=success")
    else:
        logger.info("daily update step=fund_list status=skip")

    cursor = None
    after_fund_code = None
    if options.use_cursor and not options.selector.fund_code:
        cursor_connection = connect(db_config)
        try:
            cursor = get_crawl_cursor(cursor_connection, options.cursor_job_name, options.cursor_date)
        finally:
            cursor_connection.close()

        if cursor and cursor.completed:
            logger.info(
                "daily update cursor job=%s date=%s status=completed last_fund_code=%s, skip",
                options.cursor_job_name,
                options.cursor_date,
                cursor.last_fund_code,
            )
            return 0, 0
        if cursor and cursor.last_fund_code:
            after_fund_code = cursor.last_fund_code
            logger.info(
                "daily update cursor job=%s date=%s resume after fund_code=%s",
                options.cursor_job_name,
                options.cursor_date,
                after_fund_code,
            )
        else:
            logger.info(
                "daily update cursor job=%s date=%s status=new",
                options.cursor_job_name,
                options.cursor_date,
            )
    elif options.selector.fund_code:
        logger.info("daily update cursor disabled because a single fund_code was specified")
    else:
        logger.info("daily update cursor disabled by config")

    fund_codes = load_selected_fund_codes(db_config, options.selector, after_fund_code=after_fund_code)
    logger.info(
        "daily update loaded %s fund codes, selector fund_code=%s start_code=%s limit=%s offset=%s after_fund_code=%s",
        len(fund_codes),
        options.selector.fund_code or "-",
        options.selector.fund_start_code or "-",
        options.selector.fund_limit,
        options.selector.fund_offset,
        after_fund_code or "-",
    )
    logger.info(
        "daily update config profile=%s nav=%s feature=%s rating=%s holdings=%s nav_start=%s nav_end=%s nav_max_pages=%s refresh_days_nav=%s refresh_days_profile=%s refresh_days_feature=%s refresh_days_rating=%s refresh_days_holding=%s rating_page_size=%s rating_max_pages=%s holding_top_line=%s",
        options.crawl_profile,
        options.crawl_nav,
        options.crawl_feature,
        options.crawl_rating,
        options.crawl_holdings,
        options.nav_start_date or "-",
        options.nav_end_date or "-",
        options.nav_max_pages,
        options.nav_refresh_days,
        options.profile_refresh_days,
        options.feature_refresh_days,
        options.rating_refresh_days,
        options.holding_refresh_days,
        options.rating_page_size,
        options.rating_max_pages,
        options.holding_top_line,
    )

    profile_spider = EastMoneyProfileSpider(request_config) if options.crawl_profile else None
    nav_spider = EastMoneyNavSpider(request_config) if options.crawl_nav else None
    feature_spider = EastMoneyFeatureSpider(request_config) if options.crawl_feature else None
    rating_spider = EastMoneyRatingSpider(request_config) if options.crawl_rating else None
    holding_spider = EastMoneyHoldingSpider(request_config) if options.crawl_holdings else None

    succeeded = 0
    failed = 0
    totals = {
        "profile": 0,
        "nav": 0,
        "feature": 0,
        "rating": 0,
        "holding": 0,
    }

    for index, fund_code in enumerate(fund_codes, start=1):
        logger.info("daily update fund=%s index=%s/%s status=start", fund_code, index, len(fund_codes))
        fund_counts = {
            "profile": 0,
            "nav": 0,
            "feature": 0,
            "rating": 0,
            "holding": 0,
        }
        connection = connect(db_config)
        try:
            if profile_spider is not None:
                if (
                    fund_data_recently_refreshed(connection, "profile", fund_code, options.profile_refresh_days)
                    or fund_profile_recently_updated(connection, fund_code, options.profile_refresh_days)
                ):
                    log_fund_step(fund_code, "profile", "skip", reason="recent", refresh_days=options.profile_refresh_days)
                else:
                    log_fund_step(fund_code, "profile", "start")
                    profile = profile_spider.fetch_profile(fund_code)
                    update_fund_profile(connection, profile)
                    upsert_fund_refresh_state(connection, fund_code, "profile", 1)
                    fund_counts["profile"] = 1
                    totals["profile"] += 1
                    log_fund_step(
                        fund_code,
                        "profile",
                        "success",
                        inception=profile.inception_date,
                        manager=profile.fund_manager,
                        fund_type=profile.fund_type,
                        company=profile.management_company,
                        scale=profile.net_asset_scale,
                        scale_date=profile.scale_date,
                    )
            else:
                log_fund_step(fund_code, "profile", "skip")

            if nav_spider is not None:
                if should_skip_nav_refresh(connection, fund_code, options):
                    log_fund_step(fund_code, "nav", "skip", reason="recent", refresh_days=options.nav_refresh_days)
                else:
                    log_fund_step(
                        fund_code,
                        "nav",
                        "start",
                        page_size=options.nav_page_size,
                        start_page=options.nav_start_page,
                        max_pages=options.nav_max_pages,
                        start_date=options.nav_start_date or "-",
                        end_date=options.nav_end_date or "-",
                    )
                    nav_saved = 0
                    nav_parsed = 0
                    for page in nav_spider.iter_pages(
                        fund_code=fund_code,
                        page_size=options.nav_page_size,
                        start_page=options.nav_start_page,
                        max_pages=options.nav_max_pages,
                        start_date=options.nav_start_date,
                        end_date=options.nav_end_date,
                    ):
                        saved = upsert_nav_history(connection, page.rows)
                        nav_saved += saved
                        nav_parsed += len(page.rows)
                        logger.info(
                            "daily update fund=%s step=nav page=%s/%s parsed=%s saved=%s total_count=%s",
                            fund_code,
                            page.page_index,
                            page.total_pages,
                            len(page.rows),
                            saved,
                            page.total_count,
                        )
                    upsert_fund_refresh_state(connection, fund_code, "nav", nav_parsed)
                    fund_counts["nav"] = nav_saved
                    totals["nav"] += nav_saved
                    log_fund_step(fund_code, "nav", "success", parsed=nav_parsed, saved=nav_saved)
            else:
                log_fund_step(fund_code, "nav", "skip")

            if feature_spider is not None:
                if (
                    fund_data_recently_refreshed(connection, "feature", fund_code, options.feature_refresh_days)
                    or fund_table_recently_updated(connection, "fund_feature_data", fund_code, options.feature_refresh_days)
                ):
                    log_fund_step(fund_code, "feature", "skip", reason="recent", refresh_days=options.feature_refresh_days)
                else:
                    log_fund_step(fund_code, "feature", "start")
                    feature_rows = feature_spider.fetch_feature_data(fund_code)
                    feature_saved = upsert_feature_data(connection, feature_rows)
                    upsert_fund_refresh_state(connection, fund_code, "feature", len(feature_rows))
                    fund_counts["feature"] = feature_saved
                    totals["feature"] += feature_saved
                    log_fund_step(fund_code, "feature", "success", parsed=len(feature_rows), saved=feature_saved)
            else:
                log_fund_step(fund_code, "feature", "skip")

            if rating_spider is not None:
                if (
                    fund_data_recently_refreshed(connection, "rating", fund_code, options.rating_refresh_days)
                    or fund_table_recently_updated(connection, "fund_rating", fund_code, options.rating_refresh_days)
                ):
                    log_fund_step(fund_code, "rating", "skip", reason="recent", refresh_days=options.rating_refresh_days)
                else:
                    log_fund_step(
                        fund_code,
                        "rating",
                        "start",
                        page_size=options.rating_page_size,
                        max_pages=options.rating_max_pages,
                    )
                    rating_rows = rating_spider.fetch_ratings(
                        fund_code,
                        page_size=options.rating_page_size,
                        max_pages=options.rating_max_pages,
                    )
                    rating_saved = upsert_fund_ratings(connection, rating_rows)
                    upsert_fund_refresh_state(connection, fund_code, "rating", len(rating_rows))
                    fund_counts["rating"] = rating_saved
                    totals["rating"] += rating_saved
                    log_fund_step(fund_code, "rating", "success", parsed=len(rating_rows), saved=rating_saved)
            else:
                log_fund_step(fund_code, "rating", "skip")

            if holding_spider is not None:
                if (
                    fund_data_recently_refreshed(connection, "holding", fund_code, options.holding_refresh_days)
                    or fund_table_recently_updated(connection, "fund_stock_holding", fund_code, options.holding_refresh_days)
                ):
                    log_fund_step(fund_code, "holding", "skip", reason="recent", refresh_days=options.holding_refresh_days)
                else:
                    log_fund_step(
                        fund_code,
                        "holding",
                        "start",
                        top_line=options.holding_top_line,
                        year=options.holding_year or "-",
                        month=options.holding_month or "-",
                    )
                    holding_rows = holding_spider.fetch_holdings(
                        fund_code,
                        top_line=options.holding_top_line,
                        year=options.holding_year,
                        month=options.holding_month,
                    )
                    holding_saved = upsert_stock_holdings(connection, holding_rows)
                    upsert_fund_refresh_state(connection, fund_code, "holding", len(holding_rows))
                    fund_counts["holding"] = holding_saved
                    totals["holding"] += holding_saved
                    log_fund_step(fund_code, "holding", "success", parsed=len(holding_rows), saved=holding_saved)
            else:
                log_fund_step(fund_code, "holding", "skip")

            succeeded += 1
            if options.use_cursor and not options.selector.fund_code:
                upsert_crawl_cursor(connection, options.cursor_job_name, options.cursor_date, fund_code, False)
                logger.info(
                    "daily update cursor job=%s date=%s last_fund_code=%s completed=0",
                    options.cursor_job_name,
                    options.cursor_date,
                    fund_code,
                )
            logger.info(
                "daily update fund=%s index=%s/%s status=success profile=%s nav_saved=%s feature_saved=%s rating_saved=%s holding_saved=%s totals_profile=%s totals_nav=%s totals_feature=%s totals_rating=%s totals_holding=%s",
                fund_code,
                index,
                len(fund_codes),
                fund_counts["profile"],
                fund_counts["nav"],
                fund_counts["feature"],
                fund_counts["rating"],
                fund_counts["holding"],
                totals["profile"],
                totals["nav"],
                totals["feature"],
                totals["rating"],
                totals["holding"],
            )
        except Exception:
            failed += 1
            logger.exception(
                "daily update fund=%s index=%s/%s status=failed completed_counts=%s",
                fund_code,
                index,
                len(fund_codes),
                fund_counts,
            )
            logger.info("daily update stops at failed fund=%s so cursor will not skip it", fund_code)
            break
        finally:
            connection.close()

    if options.use_cursor and not options.selector.fund_code and failed == 0:
        cursor_connection = connect(db_config)
        try:
            last_fund_code = fund_codes[-1] if fund_codes else after_fund_code
            completed = options.selector.fund_limit is None or len(fund_codes) < options.selector.fund_limit
            upsert_crawl_cursor(
                cursor_connection,
                options.cursor_job_name,
                options.cursor_date,
                last_fund_code,
                completed,
            )
            logger.info(
                "daily update cursor job=%s date=%s last_fund_code=%s completed=%s",
                options.cursor_job_name,
                options.cursor_date,
                last_fund_code,
                int(completed),
            )
        finally:
            cursor_connection.close()

    logger.info(
        "daily update completed, succeeded: %s, failed: %s, profiles: %s, nav rows: %s, feature rows: %s, rating rows: %s, holding rows: %s",
        succeeded,
        failed,
        totals["profile"],
        totals["nav"],
        totals["feature"],
        totals["rating"],
        totals["holding"],
    )
    return succeeded, failed


def log_fund_step(fund_code: str, step: str, status: str, **details) -> None:
    suffix = ""
    if details:
        suffix = " " + " ".join(f"{key}={value}" for key, value in details.items())
    logger.info("daily update fund=%s step=%s status=%s%s", fund_code, step, status, suffix)


def should_skip_nav_refresh(connection, fund_code: str, options: DailyUpdateOptions) -> bool:
    if options.nav_refresh_days <= 0:
        return False
    if options.nav_start_date or options.nav_end_date:
        return False
    if options.nav_start_page != 1:
        return False
    if options.nav_max_pages not in (None, 1):
        return False
    return fund_data_recently_refreshed(connection, "nav", fund_code, options.nav_refresh_days)


def load_selected_fund_codes(
    db_config: DatabaseConfig,
    selector: BatchSelector,
    after_fund_code: str | None = None,
) -> list[str]:
    if selector.fund_code:
        return [selector.fund_code]

    connection = connect(db_config)
    try:
        return list_fund_codes(
            connection,
            limit=selector.fund_limit,
            offset=selector.fund_offset,
            start_fund_code=selector.fund_start_code,
            after_fund_code=after_fund_code,
        )
    finally:
        connection.close()


def selector_from_env(single_code_env: str = "FUND_CODE") -> BatchSelector:
    return BatchSelector(
        fund_code=os.getenv(single_code_env, os.getenv("FUND_CODE", "")).strip(),
        fund_start_code=os.getenv("FUND_START_CODE", "").strip(),
        fund_limit=parse_optional_int(os.getenv("FUND_LIMIT"), "FUND_LIMIT"),
        fund_offset=int(os.getenv("FUND_OFFSET", "0")),
    )


def fund_list_options_from_env() -> FundListOptions:
    return FundListOptions(
        page_size=int(os.getenv("PAGE_SIZE", "200")),
        start_page=int(os.getenv("START_PAGE", "1")),
        max_pages=parse_optional_int(os.getenv("MAX_PAGES"), "MAX_PAGES"),
    )


def nav_options_from_env() -> NavOptions:
    return NavOptions(
        fund_code=os.getenv("NAV_FUND_CODE", "519674").strip(),
        page_size=int(os.getenv("NAV_PAGE_SIZE", "20")),
        start_page=int(os.getenv("NAV_START_PAGE", "1")),
        max_pages=parse_optional_int(os.getenv("NAV_MAX_PAGES"), "NAV_MAX_PAGES"),
        start_date=normalize_query_date(os.getenv("NAV_START_DATE", "")),
        end_date=normalize_query_date(os.getenv("NAV_END_DATE", "")),
    )


def profile_nav_options_from_env() -> ProfileNavOptions:
    return ProfileNavOptions(
        selector=selector_from_env("FUND_CODE"),
        crawl_profile=parse_bool(os.getenv("CRAWL_PROFILE", "1")),
        crawl_nav=parse_bool(os.getenv("CRAWL_NAV", "1")),
        nav_page_size=int(os.getenv("NAV_PAGE_SIZE", "20")),
        nav_start_page=int(os.getenv("NAV_START_PAGE", "1")),
        nav_max_pages=parse_optional_int(os.getenv("NAV_MAX_PAGES"), "NAV_MAX_PAGES"),
        nav_start_date=normalize_query_date(os.getenv("NAV_START_DATE", "")),
        nav_end_date=normalize_query_date(os.getenv("NAV_END_DATE", "")),
    )


def feature_options_from_env() -> FeatureOptions:
    return FeatureOptions(selector=selector_from_env("FEATURE_FUND_CODE"))


def rating_options_from_env() -> RatingOptions:
    return RatingOptions(
        selector=selector_from_env("RATING_FUND_CODE"),
        page_size=int(os.getenv("RATING_PAGE_SIZE", "50")),
        max_pages=parse_optional_int(os.getenv("RATING_MAX_PAGES"), "RATING_MAX_PAGES"),
    )


def holding_options_from_env() -> HoldingOptions:
    return HoldingOptions(
        selector=selector_from_env("HOLDING_FUND_CODE"),
        top_line=int(os.getenv("HOLDING_TOP_LINE", "10")),
        year=os.getenv("HOLDING_YEAR", "").strip(),
        month=os.getenv("HOLDING_MONTH", "").strip(),
    )


def daily_update_options_from_env() -> DailyUpdateOptions:
    nav_start_date = normalize_query_date(os.getenv("NAV_START_DATE", os.getenv("DAILY_NAV_START_DATE", "")))
    nav_end_date = normalize_query_date(os.getenv("NAV_END_DATE", os.getenv("DAILY_NAV_END_DATE", "")))
    nav_max_pages = parse_optional_int(os.getenv("NAV_MAX_PAGES", os.getenv("DAILY_NAV_MAX_PAGES", "")), "NAV_MAX_PAGES")
    if nav_max_pages is None and not nav_start_date and not nav_end_date:
        nav_max_pages = 1
    cursor_date = os.getenv("DAILY_CURSOR_DATE", "").strip() or datetime.now().strftime("%Y%m%d")

    return DailyUpdateOptions(
        selector=selector_from_env("FUND_CODE"),
        refresh_fund_list=parse_bool(os.getenv("DAILY_CRAWL_FUND_LIST", "1")),
        crawl_profile=parse_bool(os.getenv("DAILY_CRAWL_PROFILE", "1")),
        crawl_nav=parse_bool(os.getenv("DAILY_CRAWL_NAV", "1")),
        crawl_feature=parse_bool(os.getenv("DAILY_CRAWL_FEATURE", "1")),
        crawl_rating=parse_bool(os.getenv("DAILY_CRAWL_RATING", "1")),
        crawl_holdings=parse_bool(os.getenv("DAILY_CRAWL_HOLDINGS", "1")),
        fund_list_options=fund_list_options_from_env(),
        nav_page_size=int(os.getenv("NAV_PAGE_SIZE", "20")),
        nav_start_page=int(os.getenv("NAV_START_PAGE", "1")),
        nav_max_pages=nav_max_pages,
        nav_start_date=nav_start_date,
        nav_end_date=nav_end_date,
        nav_refresh_days=int(os.getenv("DAILY_NAV_REFRESH_DAYS", "1")),
        profile_refresh_days=int(os.getenv("DAILY_PROFILE_REFRESH_DAYS", "30")),
        feature_refresh_days=int(os.getenv("DAILY_FEATURE_REFRESH_DAYS", "7")),
        rating_refresh_days=int(os.getenv("DAILY_RATING_REFRESH_DAYS", "7")),
        holding_refresh_days=int(os.getenv("DAILY_HOLDING_REFRESH_DAYS", "7")),
        rating_page_size=int(os.getenv("RATING_PAGE_SIZE", "50")),
        rating_max_pages=parse_optional_int(
            os.getenv("DAILY_RATING_MAX_PAGES", os.getenv("RATING_MAX_PAGES", "1")),
            "DAILY_RATING_MAX_PAGES",
        ),
        holding_top_line=int(os.getenv("HOLDING_TOP_LINE", "10")),
        holding_year=os.getenv("HOLDING_YEAR", "").strip(),
        holding_month=os.getenv("HOLDING_MONTH", "").strip(),
        use_cursor=parse_bool(os.getenv("DAILY_USE_CURSOR", "1")),
        cursor_date=cursor_date,
        cursor_job_name=os.getenv("DAILY_CURSOR_JOB_NAME", "daily_update").strip() or "daily_update",
    )
