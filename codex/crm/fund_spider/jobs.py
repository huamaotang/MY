from __future__ import annotations

import logging
import os
from dataclasses import dataclass

from db import (
    DatabaseConfig,
    connect,
    database_config_from_env,
    ensure_schema,
    list_fund_codes,
    update_fund_profile,
    upsert_feature_data,
    upsert_fund_rankings,
    upsert_fund_ratings,
    upsert_fund_summaries,
    upsert_nav_history,
    upsert_sina_finance_news,
    upsert_stock_holdings,
    upsert_stock_quotes,
    upsert_yangjibao_news,
)
from settings import normalize_query_date, parse_optional_int, request_config_from_env
from spiders.feature_spider import EastMoneyFeatureSpider
from spiders.fund_ranking_spider import EastMoneyFundSpider, RequestConfig
from spiders.holding_spider import EastMoneyHoldingSpider
from spiders.nav_spider import EastMoneyNavSpider
from spiders.profile_spider import EastMoneyProfileSpider
from spiders.rating_spider import EastMoneyRatingSpider
from spiders.sina_news_spider import SINA_NEWS_CATEGORIES, SinaNewsSpider
from spiders.stock_spider import EastMoneyStockSpider
from spiders.yangjibao_news_spider import YangjibaoNewsSpider


logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class BatchSelector:
    fund_code: str = ""
    fund_start_code: str = ""
    fund_limit: int | None = None
    fund_offset: int = 0

    @property
    def is_unfiltered(self) -> bool:
        return (
            not self.fund_code
            and not self.fund_start_code
            and self.fund_limit is None
            and self.fund_offset == 0
        )


@dataclass(frozen=True)
class RankingOptions:
    page_size: int = 50
    start_page: int = 1
    max_pages: int | None = None


@dataclass(frozen=True)
class BasicOptions:
    selector: BatchSelector
    ranking: RankingOptions
    refresh_list: bool = True


@dataclass(frozen=True)
class NavHistoryOptions:
    selector: BatchSelector
    page_size: int = 20
    start_page: int = 1
    max_pages: int | None = None
    start_date: str = ""
    end_date: str = ""
    page_workers: int = 4
    write_batch_size: int = 200


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


def crawl_nav_performance(
    options: RankingOptions | None = None,
    db_config: DatabaseConfig | None = None,
    request_config: RequestConfig | None = None,
) -> int:
    options = options or ranking_options_from_env()
    db_config = db_config or database_config_from_env()
    request_config = request_config or request_config_from_env()

    ensure_schema(db_config)
    spider = EastMoneyFundSpider(request_config)
    connection = connect(db_config)
    total_saved = 0
    try:
        for page in spider.iter_pages(
            start_page=options.start_page,
            page_size=options.page_size,
            max_pages=options.max_pages,
        ):
            saved = upsert_fund_rankings(connection, page.funds)
            total_saved += saved
            logger.info(
                "step=nav_performance page=%s/%s parsed=%s saved=%s total_records=%s",
                page.page_index,
                page.total_pages,
                len(page.funds),
                saved,
                page.total_records,
            )
    finally:
        connection.close()

    logger.info("step=nav_performance status=completed saved=%s", total_saved)
    return total_saved


def crawl_basic(
    options: BasicOptions | None = None,
    db_config: DatabaseConfig | None = None,
    request_config: RequestConfig | None = None,
) -> tuple[int, int, int]:
    options = options or basic_options_from_env()
    db_config = db_config or database_config_from_env()
    request_config = request_config or request_config_from_env()

    ensure_schema(db_config)
    summary_saved = 0
    if options.refresh_list:
        ranking_spider = EastMoneyFundSpider(request_config)
        connection = connect(db_config)
        try:
            for page in ranking_spider.iter_pages(
                start_page=options.ranking.start_page,
                page_size=options.ranking.page_size,
                max_pages=options.ranking.max_pages,
            ):
                summary_saved += upsert_fund_summaries(connection, page.funds)
                logger.info(
                    "step=basic_list page=%s/%s parsed=%s total_saved=%s",
                    page.page_index,
                    page.total_pages,
                    len(page.funds),
                    summary_saved,
                )
        finally:
            connection.close()

    fund_codes = load_selected_fund_codes(db_config, options.selector, require_existing=True)
    if options.selector.fund_code and not fund_codes:
        raise ValueError(
            f"fund {options.selector.fund_code} does not exist in fund_detail; "
            "run `python cli.py basic` once to initialize the fund list"
        )

    spider = EastMoneyProfileSpider(request_config)
    succeeded = 0
    failed = 0
    for index, fund_code in enumerate(fund_codes, start=1):
        connection = connect(db_config)
        try:
            profile = spider.fetch_profile(fund_code)
            affected = update_fund_profile(connection, profile)
            if affected == 0:
                raise ValueError(f"fund {fund_code} does not exist in fund_detail")
            succeeded += 1
            logger.info(
                "step=basic_profile fund=%s index=%s/%s status=success",
                fund_code,
                index,
                len(fund_codes),
            )
        except Exception:
            failed += 1
            logger.exception(
                "step=basic_profile fund=%s index=%s/%s status=failed",
                fund_code,
                index,
                len(fund_codes),
            )
        finally:
            connection.close()

    logger.info(
        "step=basic status=completed summaries=%s succeeded=%s failed=%s",
        summary_saved,
        succeeded,
        failed,
    )
    return summary_saved, succeeded, failed


def crawl_nav_history(
    options: NavHistoryOptions | None = None,
    db_config: DatabaseConfig | None = None,
    request_config: RequestConfig | None = None,
) -> tuple[int, int, int]:
    options = options or nav_history_options_from_env()
    validate_date_range(options.start_date, options.end_date)
    if options.page_workers < 1:
        raise ValueError("NAV_PAGE_WORKERS must be greater than or equal to 1")
    if options.write_batch_size < 1:
        raise ValueError("NAV_WRITE_BATCH_SIZE must be greater than or equal to 1")
    db_config = db_config or database_config_from_env()
    request_config = request_config or request_config_from_env()

    logger.info(
        "step=nav_history status=started fund_code=%s fund_start_code=%s "
        "fund_limit=%s fund_offset=%s page_size=%s start_page=%s max_pages=%s "
        "start_date=%s end_date=%s page_workers=%s write_batch_size=%s "
        "request_min_delay=%s request_max_delay=%s request_timeout=%s request_retries=%s "
        "db_host=%s db_port=%s db_name=%s",
        options.selector.fund_code or "-",
        options.selector.fund_start_code or "-",
        options.selector.fund_limit,
        options.selector.fund_offset,
        options.page_size,
        options.start_page,
        options.max_pages,
        options.start_date or "-",
        options.end_date or "-",
        options.page_workers,
        options.write_batch_size,
        request_config.min_delay_seconds,
        request_config.max_delay_seconds,
        request_config.timeout_seconds,
        request_config.max_retries,
        db_config.host,
        db_config.port,
        db_config.database,
    )
    ensure_schema(db_config)
    fund_codes = load_selected_fund_codes(db_config, options.selector, require_existing=True)
    if options.selector.fund_code and not fund_codes:
        raise ValueError(f"fund {options.selector.fund_code} does not exist in fund_detail")
    logger.info(
        "step=nav_history action=select_funds selected=%s first=%s last=%s",
        len(fund_codes),
        fund_codes[0] if fund_codes else "-",
        fund_codes[-1] if fund_codes else "-",
    )

    spider = EastMoneyNavSpider(request_config)
    succeeded = 0
    failed = 0
    total_saved = 0
    for index, fund_code in enumerate(fund_codes, start=1):
        connection = connect(db_config)
        try:
            fund_saved = 0
            pending_rows = []
            for page in spider.iter_pages(
                fund_code=fund_code,
                page_size=options.page_size,
                start_page=options.start_page,
                max_pages=options.max_pages,
                start_date=options.start_date,
                end_date=options.end_date,
                page_workers=options.page_workers,
            ):
                pending_rows.extend(page.rows)
                logger.info(
                    "step=nav_history fund=%s page=%s/%s parsed=%s pending=%s",
                    fund_code,
                    page.page_index,
                    page.total_pages,
                    len(page.rows),
                    len(pending_rows),
                )
                while len(pending_rows) >= options.write_batch_size:
                    batch_rows = pending_rows[: options.write_batch_size]
                    del pending_rows[: options.write_batch_size]
                    saved = upsert_nav_history(connection, batch_rows)
                    fund_saved += saved
                    total_saved += saved
                    logger.info(
                        "step=nav_history fund=%s action=upsert rows=%s saved=%s",
                        fund_code,
                        len(batch_rows),
                        saved,
                    )
            if pending_rows:
                saved = upsert_nav_history(connection, pending_rows)
                fund_saved += saved
                total_saved += saved
                logger.info(
                    "step=nav_history fund=%s action=upsert rows=%s saved=%s",
                    fund_code,
                    len(pending_rows),
                    saved,
                )
            succeeded += 1
            logger.info(
                "step=nav_history fund=%s index=%s/%s status=success saved=%s",
                fund_code,
                index,
                len(fund_codes),
                fund_saved,
            )
        except Exception:
            failed += 1
            logger.exception(
                "step=nav_history fund=%s index=%s/%s status=failed",
                fund_code,
                index,
                len(fund_codes),
            )
        finally:
            connection.close()

    logger.info(
        "step=nav_history status=completed succeeded=%s failed=%s saved=%s",
        succeeded,
        failed,
        total_saved,
    )
    return succeeded, failed, total_saved


def crawl_feature_data(
    options: FeatureOptions | None = None,
    db_config: DatabaseConfig | None = None,
    request_config: RequestConfig | None = None,
) -> tuple[int, int, int]:
    options = options or feature_options_from_env()
    db_config = db_config or database_config_from_env()
    request_config = request_config or request_config_from_env()

    ensure_schema(db_config)
    fund_codes = load_selected_fund_codes(db_config, options.selector, require_existing=True)
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
                logger.info(
                    "step=feature fund=%s index=%s/%s saved=%s",
                    fund_code,
                    index,
                    len(fund_codes),
                    saved,
                )
            except Exception:
                failed += 1
                logger.exception("step=feature fund=%s status=failed", fund_code)
    finally:
        connection.close()
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
    fund_codes = load_selected_fund_codes(db_config, options.selector, require_existing=True)
    spider = EastMoneyRatingSpider(request_config)
    succeeded = 0
    failed = 0
    total_saved = 0
    connection = connect(db_config)
    try:
        for index, fund_code in enumerate(fund_codes, start=1):
            try:
                rows = spider.fetch_ratings(
                    fund_code,
                    page_size=options.page_size,
                    max_pages=options.max_pages,
                )
                saved = upsert_fund_ratings(connection, rows)
                total_saved += saved
                succeeded += 1
                logger.info(
                    "step=rating_history fund=%s index=%s/%s parsed=%s saved=%s",
                    fund_code,
                    index,
                    len(fund_codes),
                    len(rows),
                    saved,
                )
            except Exception:
                failed += 1
                logger.exception("step=rating_history fund=%s status=failed", fund_code)
    finally:
        connection.close()
    return succeeded, failed, total_saved


def crawl_rating_list(
    db_config: DatabaseConfig | None = None,
    request_config: RequestConfig | None = None,
) -> int:
    db_config = db_config or database_config_from_env()
    request_config = request_config or request_config_from_env()
    ensure_schema(db_config)
    rows = EastMoneyRatingSpider(request_config).fetch_rating_list()
    connection = connect(db_config)
    try:
        saved = upsert_fund_ratings(connection, rows)
    finally:
        connection.close()
    logger.info("step=rating_current parsed=%s saved=%s", len(rows), saved)
    return saved


def crawl_holdings(
    options: HoldingOptions | None = None,
    db_config: DatabaseConfig | None = None,
    request_config: RequestConfig | None = None,
) -> tuple[int, int, int]:
    options = options or holding_options_from_env()
    db_config = db_config or database_config_from_env()
    request_config = request_config or request_config_from_env()
    ensure_schema(db_config)
    fund_codes = load_selected_fund_codes(db_config, options.selector, require_existing=True)
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
                    "step=holdings fund=%s index=%s/%s parsed=%s saved=%s",
                    fund_code,
                    index,
                    len(fund_codes),
                    len(rows),
                    saved,
                )
            except Exception:
                failed += 1
                logger.exception("step=holdings fund=%s status=failed", fund_code)
    finally:
        connection.close()
    return succeeded, failed, total_saved


def crawl_yangjibao_news(
    score: int | None = None,
    db_config: DatabaseConfig | None = None,
    request_config: RequestConfig | None = None,
) -> int:
    db_config = db_config or database_config_from_env()
    request_config = request_config or request_config_from_env()
    score = int(os.getenv("YJB_NEWS_SCORE", "2")) if score is None else score
    ensure_schema(db_config)
    rows = YangjibaoNewsSpider(request_config).fetch_news(score)
    connection = connect(db_config)
    try:
        saved = upsert_yangjibao_news(connection, rows)
    finally:
        connection.close()
    return saved


def crawl_sina_news(
    max_pages: int | None = None,
    db_config: DatabaseConfig | None = None,
    request_config: RequestConfig | None = None,
) -> int:
    db_config = db_config or database_config_from_env()
    request_config = request_config or request_config_from_env()
    max_pages = int(os.getenv("SINA_NEWS_MAX_PAGES", "1")) if max_pages is None else max_pages
    ensure_schema(db_config)
    default_tags = ",".join(str(tag) for tag in SINA_NEWS_CATEGORIES)
    tags = [
        int(value.strip())
        for value in os.getenv(
            "SINA_NEWS_TAGS",
            os.getenv("SINA_NEWS_TAG", default_tags),
        ).split(",")
        if value.strip()
    ]
    spider = SinaNewsSpider(request_config)
    rows = []
    for tag in tags:
        rows.extend(
            spider.fetch_news(
                page_size=int(os.getenv("SINA_NEWS_PAGE_SIZE", "20")),
                max_pages=max_pages,
                tag=tag,
            )
        )
    connection = connect(db_config)
    try:
        saved = upsert_sina_finance_news(connection, rows)
    finally:
        connection.close()
    return saved


def crawl_stocks(
    db_config: DatabaseConfig | None = None,
    request_config: RequestConfig | None = None,
    market: str | None = None,
) -> int:
    db_config = db_config or database_config_from_env()
    request_config = request_config or request_config_from_env()
    market = (market or os.getenv("STOCK_MARKET", "cn")).strip().lower()
    ensure_schema(db_config)
    rows = EastMoneyStockSpider(request_config).fetch_all(
        page_size=int(os.getenv("STOCK_PAGE_SIZE", "100")),
        market=market,
    )
    connection = connect(db_config)
    try:
        saved = upsert_stock_quotes(connection, rows)
    finally:
        connection.close()
    trade_dates = sorted({row.trade_date for row in rows})
    logger.info(
        "step=stock market=%s trade_dates=%s parsed=%s saved=%s",
        market,
        trade_dates,
        len(rows),
        saved,
    )
    return saved


def load_selected_fund_codes(
    db_config: DatabaseConfig,
    selector: BatchSelector,
    require_existing: bool = False,
) -> list[str]:
    connection = connect(db_config)
    try:
        if selector.fund_code:
            if not require_existing:
                return [selector.fund_code]
            codes = list_fund_codes(
                connection,
                start_fund_code=selector.fund_code,
                limit=1,
            )
            return [selector.fund_code] if codes and codes[0] == selector.fund_code else []
        return list_fund_codes(
            connection,
            limit=selector.fund_limit,
            offset=selector.fund_offset,
            start_fund_code=selector.fund_start_code,
        )
    finally:
        connection.close()


def validate_date_range(start_date: str, end_date: str) -> None:
    if start_date and end_date and start_date > end_date:
        raise ValueError("NAV_START_DATE must not be later than NAV_END_DATE")


def selector_from_env(single_code_env: str = "FUND_CODE") -> BatchSelector:
    return BatchSelector(
        fund_code=os.getenv(single_code_env, os.getenv("FUND_CODE", "")).strip(),
        fund_start_code=os.getenv("FUND_START_CODE", "").strip(),
        fund_limit=parse_optional_int(os.getenv("FUND_LIMIT"), "FUND_LIMIT"),
        fund_offset=int(os.getenv("FUND_OFFSET", "0")),
    )


def ranking_options_from_env() -> RankingOptions:
    return RankingOptions(
        page_size=int(os.getenv("PAGE_SIZE", "50")),
        start_page=int(os.getenv("START_PAGE", "1")),
        max_pages=parse_optional_int(os.getenv("MAX_PAGES"), "MAX_PAGES"),
    )


def basic_options_from_env() -> BasicOptions:
    selector = selector_from_env()
    refresh_default = "1" if selector.is_unfiltered else "0"
    return BasicOptions(
        selector=selector,
        ranking=ranking_options_from_env(),
        refresh_list=os.getenv("BASIC_REFRESH_LIST", refresh_default).strip().lower()
        in {"1", "true", "yes", "on"},
    )


def nav_history_options_from_env() -> NavHistoryOptions:
    start_date = normalize_query_date(os.getenv("NAV_START_DATE", ""))
    end_date = normalize_query_date(os.getenv("NAV_END_DATE", ""))
    validate_date_range(start_date, end_date)
    return NavHistoryOptions(
        selector=selector_from_env("NAV_FUND_CODE"),
        page_size=int(os.getenv("NAV_PAGE_SIZE", "20")),
        start_page=int(os.getenv("NAV_START_PAGE", "1")),
        max_pages=parse_optional_int(os.getenv("NAV_MAX_PAGES"), "NAV_MAX_PAGES"),
        start_date=start_date,
        end_date=end_date,
        page_workers=int(os.getenv("NAV_PAGE_WORKERS", "4")),
        write_batch_size=int(os.getenv("NAV_WRITE_BATCH_SIZE", "200")),
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
