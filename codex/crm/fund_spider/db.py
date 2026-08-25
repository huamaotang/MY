from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timedelta
import logging
import os
import re
from typing import TYPE_CHECKING, Any, Iterable, Sequence

import pymysql
from pymysql.connections import Connection

if TYPE_CHECKING:
    from spiders.fund_ranking_spider import FundRankingRow
    from spiders.stock_spider import StockQuote


logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class DatabaseConfig:
    host: str
    port: int
    user: str
    password: str
    database: str


def database_config_from_env() -> DatabaseConfig:
    return DatabaseConfig(
        host=os.getenv("DB_HOST", "127.0.0.1"),
        port=int(os.getenv("DB_PORT", "3306")),
        user=os.getenv("DB_USER", "root"),
        password=os.getenv("DB_PASSWORD", "qwer8989"),
        database=os.getenv("DB_NAME", "fund"),
    )


@dataclass(frozen=True)
class FundNavHistory:
    fund_code: str
    nav_date: str
    unit_nav: str | None
    accumulated_nav: str | None
    daily_growth_rate: str | None


@dataclass(frozen=True)
class FundProfile:
    fund_code: str
    inception_date: str | None
    fund_manager: str | None
    fund_type: str | None
    management_company: str | None
    net_asset_scale: str | None
    scale_date: str | None


@dataclass(frozen=True)
class FundFeatureData:
    fund_code: str
    period_label: str
    cutoff_date: str
    standard_deviation: str | None
    sharpe_ratio: str | None


@dataclass(frozen=True)
class FundRating:
    fund_code: str
    rating_date: str
    zhaoshang_rating: int | None
    shanghai_rating_3y: int | None
    shanghai_rating_5y: int | None
    jian_rating: int | None
    morning_star_rating: int | None


@dataclass(frozen=True)
class FundStockHolding:
    fund_code: str
    report_period: str | None
    report_date: str
    cutoff_date: str
    rank_no: int | None
    stock_code: str
    stock_name: str | None
    latest_price: str | None
    change_rate: str | None
    related_info_url: str | None
    net_value_ratio: str | None
    holding_shares_10k: str | None
    holding_market_value_10k: str | None


@dataclass(frozen=True)
class CrawlCursor:
    job_name: str
    cursor_date: str
    last_fund_code: str | None
    completed: bool


@dataclass(frozen=True)
class YangjibaoNews:
    news_id: str
    title: str | None
    content: str
    display_time: str
    images_json: str
    score: int | None
    news_type: int | None
    source_json: str


@dataclass(frozen=True)
class SinaFinanceNews:
    news_id: str
    category_tag: int
    category_name: str
    content: str
    create_time: str
    update_time: str
    doc_url: str | None
    tags_json: str
    images_json: str
    source_json: str


def connect(config: DatabaseConfig) -> Connection:
    return pymysql.connect(
        host=config.host,
        port=config.port,
        user=config.user,
        password=config.password,
        database=config.database,
        charset="utf8mb4",
        autocommit=False,
        cursorclass=pymysql.cursors.DictCursor,
    )


def ensure_schema(config: DatabaseConfig) -> None:
    database = _quote_identifier(config.database)
    connection = pymysql.connect(
        host=config.host,
        port=config.port,
        user=config.user,
        password=config.password,
        charset="utf8mb4",
        autocommit=False,
        cursorclass=pymysql.cursors.DictCursor,
    )
    try:
        with connection.cursor() as cursor:
            cursor.execute(
                f"CREATE DATABASE IF NOT EXISTS {database} "
                "DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
            )
            cursor.execute(f"USE {database}")
            cursor.execute(
                f"""
                CREATE TABLE IF NOT EXISTS {database}.fund_detail (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
                  fund_code VARCHAR(20) NOT NULL COMMENT '基金代码',
                  fund_name VARCHAR(255) NOT NULL COMMENT '基金名称',
                  inception_date DATE NULL COMMENT '成立日期',
                  fund_manager VARCHAR(255) NULL COMMENT '基金经理',
                  fund_type VARCHAR(100) NULL COMMENT '类型',
                  management_company VARCHAR(255) NULL COMMENT '管理人',
                  net_asset_scale VARCHAR(100) NULL COMMENT '净资产规模',
                  scale_date DATE NULL COMMENT '规模截止至日',
                  profile_updated_at DATETIME NULL COMMENT '基础资料更新时间',
                  can_buy TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否可购买',
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_fund_detail_code (fund_code)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基金配置表'
                """
            )
            _ensure_fund_detail_columns(cursor, config.database)
            cursor.execute(_fund_nav_history_ddl(database))
            cursor.execute(_fund_performance_history_ddl(database))
            cursor.execute(_fund_stock_holding_ddl(database))
            _ensure_fund_stock_holding_columns(cursor, config.database)
            cursor.execute(_fund_holding_import_ddl(database))
            cursor.execute(_fund_holding_import_item_ddl(database))
            cursor.execute(_user_fund_holding_ddl(database))
            _ensure_portfolio_holding_columns(cursor, config.database)
            cursor.execute(_fund_feature_data_ddl(database))
            cursor.execute(_fund_rating_ddl(database))
            cursor.execute(_fund_scale_history_ddl(database))
            cursor.execute(_fund_score_profile_ddl(database))
            cursor.execute(_fund_score_factor_snapshot_ddl(database))
            cursor.execute(_fund_score_result_ddl(database))
            cursor.execute(_fund_score_backtest_ddl(database))
            cursor.execute(_fund_score_job_ddl(database))
            _ensure_default_score_profile(cursor)
            cursor.execute(_fund_refresh_state_ddl(database))
            cursor.execute(_fund_crawl_cursor_ddl(database))
            cursor.execute(_yangjibao_news_ddl(database))
            cursor.execute(_sina_finance_news_ddl(database))
            _ensure_sina_finance_news_columns(cursor, config.database)
            cursor.execute(_stock_detail_ddl(database))
            cursor.execute(_stock_daily_history_ddl(database))
            _ensure_stock_daily_history_columns(cursor, config.database)
        connection.commit()
    finally:
        connection.close()


def upsert_funds(connection: Connection, funds: Iterable[tuple[str, str]]) -> int:
    rows = list(funds)
    if not rows:
        return 0

    sql = """
        INSERT INTO fund_detail (fund_code, fund_name)
        VALUES (%s, %s)
        ON DUPLICATE KEY UPDATE
          fund_name = VALUES(fund_name),
          updated_at = CURRENT_TIMESTAMP
    """
    log_write_sql("upsert_funds", sql, rows)
    with connection.cursor() as cursor:
        cursor.executemany(sql, rows)
    connection.commit()
    return len(rows)


def upsert_fund_summaries(connection: Connection, rows: Iterable[FundRankingRow]) -> int:
    values = [
        (row.fund_code, row.fund_name, int(row.can_buy))
        for row in rows
    ]
    if not values:
        return 0

    sql = """
        INSERT INTO fund_detail (fund_code, fund_name, can_buy)
        VALUES (%s, %s, %s)
        ON DUPLICATE KEY UPDATE
          fund_name = VALUES(fund_name),
          can_buy = VALUES(can_buy),
          updated_at = CURRENT_TIMESTAMP
    """
    log_write_sql("upsert_fund_summaries", sql, values)
    with connection.cursor() as cursor:
        cursor.executemany(sql, values)
    connection.commit()
    return len(values)


def upsert_fund_rankings(connection: Connection, rows: Iterable[FundRankingRow]) -> int:
    ranking_rows = list(rows)
    if not ranking_rows:
        return 0

    fund_values = [
        (row.fund_code, row.fund_name, int(row.can_buy))
        for row in ranking_rows
    ]
    nav_values = [
        (
            row.fund_code,
            row.nav_date,
            row.unit_nav,
            row.accumulated_nav,
            row.daily_growth_rate,
        )
        for row in ranking_rows
        if row.nav_date
    ]
    performance_values = [
        (
            row.fund_code,
            row.nav_date,
            row.fund_name_pinyin,
            row.inception_date,
            row.weekly_return_rate,
            row.monthly_return_rate,
            row.three_month_return_rate,
            row.six_month_return_rate,
            row.one_year_return_rate,
            row.two_year_return_rate,
            row.three_year_return_rate,
            row.year_to_date_return_rate,
            row.since_inception_return_rate,
            row.custom_start_date,
            row.custom_end_date,
            row.custom_return_rate,
            row.sale_status,
            row.original_fee_rate,
            row.discounted_fee_rate,
            row.discount_factor,
            row.cash_management_fee_rate,
            row.source_row,
        )
        for row in ranking_rows
        if row.nav_date
    ]

    fund_sql = """
        INSERT INTO fund_detail (fund_code, fund_name, can_buy)
        VALUES (%s, %s, %s)
        ON DUPLICATE KEY UPDATE
          fund_name = VALUES(fund_name),
          can_buy = VALUES(can_buy),
          updated_at = CURRENT_TIMESTAMP
    """
    nav_sql = """
        INSERT INTO fund_nav_history (
          fund_code, nav_date, unit_nav, accumulated_nav, daily_growth_rate
        )
        VALUES (%s, %s, %s, %s, %s)
        ON DUPLICATE KEY UPDATE
          unit_nav = VALUES(unit_nav),
          accumulated_nav = VALUES(accumulated_nav),
          daily_growth_rate = VALUES(daily_growth_rate),
          updated_at = CURRENT_TIMESTAMP
    """
    performance_sql = """
        INSERT INTO fund_performance_history (
          fund_code,
          nav_date,
          fund_name_pinyin,
          inception_date,
          weekly_return_rate,
          monthly_return_rate,
          three_month_return_rate,
          six_month_return_rate,
          one_year_return_rate,
          two_year_return_rate,
          three_year_return_rate,
          year_to_date_return_rate,
          since_inception_return_rate,
          custom_start_date,
          custom_end_date,
          custom_return_rate,
          sale_status,
          original_fee_rate,
          discounted_fee_rate,
          discount_factor,
          cash_management_fee_rate,
          source_row
        )
        VALUES (
          %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s,
          %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s
        )
        ON DUPLICATE KEY UPDATE
          fund_name_pinyin = VALUES(fund_name_pinyin),
          inception_date = VALUES(inception_date),
          weekly_return_rate = VALUES(weekly_return_rate),
          monthly_return_rate = VALUES(monthly_return_rate),
          three_month_return_rate = VALUES(three_month_return_rate),
          six_month_return_rate = VALUES(six_month_return_rate),
          one_year_return_rate = VALUES(one_year_return_rate),
          two_year_return_rate = VALUES(two_year_return_rate),
          three_year_return_rate = VALUES(three_year_return_rate),
          year_to_date_return_rate = VALUES(year_to_date_return_rate),
          since_inception_return_rate = VALUES(since_inception_return_rate),
          custom_start_date = VALUES(custom_start_date),
          custom_end_date = VALUES(custom_end_date),
          custom_return_rate = VALUES(custom_return_rate),
          sale_status = VALUES(sale_status),
          original_fee_rate = VALUES(original_fee_rate),
          discounted_fee_rate = VALUES(discounted_fee_rate),
          discount_factor = VALUES(discount_factor),
          cash_management_fee_rate = VALUES(cash_management_fee_rate),
          source_row = VALUES(source_row),
          updated_at = CURRENT_TIMESTAMP
    """
    log_write_sql("upsert_ranking_funds", fund_sql, fund_values)
    log_write_sql("upsert_ranking_nav", nav_sql, nav_values)
    log_write_sql("upsert_ranking_performance", performance_sql, performance_values)
    try:
        with connection.cursor() as cursor:
            cursor.executemany(fund_sql, fund_values)
            if nav_values:
                cursor.executemany(nav_sql, nav_values)
            if performance_values:
                cursor.executemany(performance_sql, performance_values)
        connection.commit()
    except Exception:
        connection.rollback()
        raise
    return len(ranking_rows)


def list_fund_codes(
    connection: Connection,
    limit: int | None = None,
    offset: int = 0,
    start_fund_code: str | None = None,
    after_fund_code: str | None = None,
) -> list[str]:
    sql = "SELECT fund_code FROM fund_detail"
    params: list[int | str] = []
    conditions: list[str] = []
    if start_fund_code:
        conditions.append("fund_code >= %s")
        params.append(start_fund_code)
    if after_fund_code:
        conditions.append("fund_code > %s")
        params.append(after_fund_code)
    if conditions:
        sql += " WHERE " + " AND ".join(conditions)
    sql += " ORDER BY fund_code"
    if limit is not None:
        sql += " LIMIT %s OFFSET %s"
        params.extend([limit, offset])
    log_write_sql("list_fund_codes", sql, tuple(params))
    with connection.cursor() as cursor:
        cursor.execute(sql, tuple(params))
        rows = cursor.fetchall()
    return [str(row["fund_code"]) for row in rows]


def list_fund_codes_for_refresh(
    connection: Connection,
    data_type: str,
    limit: int,
) -> list[str]:
    """Return the least recently refreshed funds first.

    Funds without a successful refresh are selected before funds that already
    have refresh-state rows.  This keeps scheduled batches bounded while still
    guaranteeing that the whole fund universe is eventually revisited.
    """
    _validate_refresh_data_type(data_type)
    if limit < 1:
        raise ValueError("refresh limit must be greater than or equal to 1")
    sql = """
        SELECT d.fund_code
        FROM fund_detail d
        LEFT JOIN fund_refresh_state r
          ON r.fund_code = d.fund_code
         AND r.data_type = %s
        ORDER BY
          CASE WHEN r.last_success_at IS NULL THEN 0 ELSE 1 END,
          r.last_success_at ASC,
          d.fund_code ASC
        LIMIT %s
    """
    with connection.cursor() as cursor:
        cursor.execute(sql, (data_type, limit))
        rows = cursor.fetchall()
    return [str(row["fund_code"]) for row in rows]


def update_fund_profile(connection: Connection, profile: FundProfile) -> int:
    sql = """
        UPDATE fund_detail
        SET
          inception_date = %s,
          fund_manager = %s,
          fund_type = %s,
          management_company = %s,
          net_asset_scale = %s,
          scale_date = %s,
          profile_updated_at = CURRENT_TIMESTAMP,
          updated_at = CURRENT_TIMESTAMP
        WHERE fund_code = %s
    """
    values = (
        profile.inception_date,
        profile.fund_manager,
        profile.fund_type,
        profile.management_company,
        profile.net_asset_scale,
        profile.scale_date,
        profile.fund_code,
    )
    log_write_sql("update_fund_profile", sql, values)
    try:
        with connection.cursor() as cursor:
            affected = cursor.execute(sql, values)
            scale_value = _parse_scale_yi(profile.net_asset_scale)
            scale_date = re.sub(r"\D", "", profile.scale_date or "")
            if scale_date:
                cursor.execute(
                    """
                    INSERT INTO fund_scale_history (
                      fund_code, scale_date, net_asset_scale_yi, source_text
                    )
                    VALUES (%s, %s, %s, %s)
                    ON DUPLICATE KEY UPDATE
                      net_asset_scale_yi = VALUES(net_asset_scale_yi),
                      source_text = VALUES(source_text),
                      updated_at = CURRENT_TIMESTAMP
                    """,
                    (
                        profile.fund_code,
                        scale_date,
                        scale_value,
                        profile.net_asset_scale,
                    ),
                )
        connection.commit()
    except Exception:
        connection.rollback()
        raise
    return affected


def _parse_scale_yi(value: str | None) -> str | None:
    if not value:
        return None
    match = re.search(r"(-?\d+(?:\.\d+)?)\s*(万亿元|亿元|万元|元)?", value.replace(",", ""))
    if not match:
        return None
    number = float(match.group(1))
    unit = match.group(2) or "亿元"
    if unit == "万亿元":
        number *= 10000
    elif unit == "万元":
        number /= 10000
    elif unit == "元":
        number /= 100000000
    return f"{number:.4f}"


def upsert_nav_history(connection: Connection, rows: Iterable[FundNavHistory]) -> int:
    values = [
        (
            row.fund_code,
            row.nav_date,
            row.unit_nav,
            row.accumulated_nav,
            row.daily_growth_rate,
        )
        for row in rows
    ]
    if not values:
        return 0

    sql = """
        INSERT INTO fund_nav_history (
          fund_code,
          nav_date,
          unit_nav,
          accumulated_nav,
          daily_growth_rate
        )
        VALUES (%s, %s, %s, %s, %s)
        ON DUPLICATE KEY UPDATE
          unit_nav = VALUES(unit_nav),
          accumulated_nav = VALUES(accumulated_nav),
          daily_growth_rate = VALUES(daily_growth_rate),
          updated_at = CURRENT_TIMESTAMP
    """
    log_write_sql("upsert_nav_history", sql, values)
    try:
        with connection.cursor() as cursor:
            cursor.executemany(sql, values)
        connection.commit()
    except Exception:
        connection.rollback()
        raise
    return len(values)


def upsert_feature_data(connection: Connection, rows: Iterable[FundFeatureData]) -> int:
    values = [
        (
            row.fund_code,
            row.period_label,
            row.cutoff_date,
            row.standard_deviation,
            row.sharpe_ratio,
        )
        for row in rows
    ]
    if not values:
        return 0

    sql = """
        INSERT INTO fund_feature_data (
          fund_code,
          period_label,
          cutoff_date,
          standard_deviation,
          sharpe_ratio
        )
        VALUES (%s, %s, %s, %s, %s)
        ON DUPLICATE KEY UPDATE
          standard_deviation = VALUES(standard_deviation),
          sharpe_ratio = VALUES(sharpe_ratio),
          updated_at = CURRENT_TIMESTAMP
    """
    log_write_sql("upsert_feature_data", sql, values)
    with connection.cursor() as cursor:
        cursor.executemany(sql, values)
    connection.commit()
    return len(values)


def upsert_fund_ratings(connection: Connection, rows: Iterable[FundRating]) -> int:
    values = [
        (
            row.fund_code,
            row.rating_date,
            row.zhaoshang_rating,
            row.shanghai_rating_3y,
            row.shanghai_rating_5y,
            row.jian_rating,
            row.morning_star_rating,
        )
        for row in rows
    ]
    if not values:
        return 0

    sql = """
        INSERT INTO fund_rating (
          fund_code,
          rating_date,
          zhaoshang_rating,
          shanghai_rating_3y,
          shanghai_rating_5y,
          jian_rating,
          morning_star_rating
        )
        VALUES (%s, %s, %s, %s, %s, %s, %s)
        ON DUPLICATE KEY UPDATE
          zhaoshang_rating = VALUES(zhaoshang_rating),
          shanghai_rating_3y = VALUES(shanghai_rating_3y),
          shanghai_rating_5y = VALUES(shanghai_rating_5y),
          jian_rating = VALUES(jian_rating),
          morning_star_rating = VALUES(morning_star_rating),
          updated_at = CURRENT_TIMESTAMP
    """
    log_write_sql("upsert_fund_ratings", sql, values)
    with connection.cursor() as cursor:
        cursor.executemany(sql, values)
    connection.commit()
    return len(values)


def upsert_yangjibao_news(connection: Connection, rows: Iterable[YangjibaoNews]) -> int:
    values = [(row.news_id, row.title, row.content, row.display_time, row.images_json,
               row.score, row.news_type, row.source_json) for row in rows]
    if not values:
        return 0
    sql = """
        INSERT INTO yangjibao_news
          (news_id, title, content, display_time, images_json, score, news_type, source_json)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
        ON DUPLICATE KEY UPDATE
          title=VALUES(title), content=VALUES(content), display_time=VALUES(display_time),
          images_json=VALUES(images_json), score=VALUES(score), news_type=VALUES(news_type),
          source_json=VALUES(source_json), updated_at=CURRENT_TIMESTAMP
    """
    log_write_sql("upsert_yangjibao_news", sql, values)
    with connection.cursor() as cursor:
        cursor.executemany(sql, values)
    connection.commit()
    return len(values)


def upsert_sina_finance_news(connection: Connection, rows: Iterable[SinaFinanceNews]) -> int:
    values = [(r.news_id, r.category_tag, r.category_name, r.content, r.create_time, r.update_time, r.doc_url, r.tags_json, r.images_json, r.source_json) for r in rows]
    if not values:
        return 0
    sql = """
      INSERT INTO sina_finance_news (news_id,category_tag,category_name,content,create_time,source_update_time,doc_url,tags_json,images_json,source_json)
      VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
      ON DUPLICATE KEY UPDATE content=VALUES(content),source_update_time=VALUES(source_update_time),doc_url=VALUES(doc_url),
        category_name=VALUES(category_name),tags_json=VALUES(tags_json),images_json=VALUES(images_json),
        source_json=VALUES(source_json),updated_at=CURRENT_TIMESTAMP
    """
    with connection.cursor() as cursor:
        cursor.executemany(sql, values)
    connection.commit()
    return len(values)


def upsert_stock_quotes(connection: Connection, rows: Iterable["StockQuote"]) -> int:
    values = list(rows)
    if not values:
        return 0
    detail_sql = """
      INSERT INTO stock_detail (stock_code,stock_name,market_code,exchange_name,listing_date)
      VALUES (%s,%s,%s,%s,%s)
      ON DUPLICATE KEY UPDATE stock_name=VALUES(stock_name),market_code=VALUES(market_code),
        exchange_name=VALUES(exchange_name),listing_date=COALESCE(VALUES(listing_date),listing_date),updated_at=CURRENT_TIMESTAMP
    """
    history_sql = """
      INSERT INTO stock_daily_history (
        stock_code,trade_date,quote_time,latest_price,change_rate,change_amount,volume,amount,amplitude,
        turnover_rate,pe_dynamic,volume_ratio,five_min_change_rate,high_price,low_price,open_price,
        previous_close,total_market_cap,float_market_cap,speed_rate,pb_ratio,change_rate_60d,
        change_rate_ytd,main_net_inflow,pe_ttm,raw_json
      ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
      ON DUPLICATE KEY UPDATE quote_time=VALUES(quote_time),latest_price=VALUES(latest_price),
        change_rate=VALUES(change_rate),change_amount=VALUES(change_amount),volume=VALUES(volume),
        amount=VALUES(amount),amplitude=VALUES(amplitude),turnover_rate=VALUES(turnover_rate),
        pe_dynamic=VALUES(pe_dynamic),volume_ratio=VALUES(volume_ratio),five_min_change_rate=VALUES(five_min_change_rate),
        high_price=VALUES(high_price),low_price=VALUES(low_price),open_price=VALUES(open_price),
        previous_close=VALUES(previous_close),total_market_cap=VALUES(total_market_cap),
        float_market_cap=VALUES(float_market_cap),speed_rate=VALUES(speed_rate),pb_ratio=VALUES(pb_ratio),
        change_rate_60d=VALUES(change_rate_60d),change_rate_ytd=VALUES(change_rate_ytd),
        main_net_inflow=VALUES(main_net_inflow),pe_ttm=VALUES(pe_ttm),raw_json=VALUES(raw_json),
        updated_at=CURRENT_TIMESTAMP
    """
    detail_values = [(r.stock_code, r.stock_name, r.market_code, r.exchange_name, r.listing_date) for r in values]
    history_values = [(
        r.stock_code,r.trade_date,r.quote_time,r.latest_price,r.change_rate,r.change_amount,r.volume,r.amount,
        r.amplitude,r.turnover_rate,r.pe_dynamic,r.volume_ratio,r.five_min_change_rate,r.high_price,r.low_price,
        r.open_price,r.previous_close,r.total_market_cap,r.float_market_cap,r.speed_rate,r.pb_ratio,
        r.change_rate_60d,r.change_rate_ytd,r.main_net_inflow,r.pe_ttm,r.raw_json
    ) for r in values]
    try:
        with connection.cursor() as cursor:
            cursor.executemany(detail_sql, detail_values)
            cursor.executemany(history_sql, history_values)
        connection.commit()
    except Exception:
        connection.rollback()
        raise
    return len(values)


def upsert_stock_holdings(connection: Connection, rows: Iterable[FundStockHolding]) -> int:
    values = [
        (
            row.fund_code,
            row.report_period,
            row.report_date,
            row.cutoff_date,
            row.rank_no,
            row.stock_code,
            row.stock_name,
            row.latest_price,
            row.change_rate,
            row.related_info_url,
            row.net_value_ratio,
            row.holding_shares_10k,
            row.holding_market_value_10k,
        )
        for row in rows
    ]
    if not values:
        return 0

    sql = """
        INSERT INTO fund_stock_holding (
          fund_code,
          report_period,
          report_date,
          cutoff_date,
          rank_no,
          stock_code,
          stock_name,
          latest_price,
          change_rate,
          related_info_url,
          net_value_ratio,
          holding_shares_10k,
          holding_market_value_10k
        )
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        ON DUPLICATE KEY UPDATE
          report_period = VALUES(report_period),
          cutoff_date = VALUES(cutoff_date),
          rank_no = VALUES(rank_no),
          stock_name = VALUES(stock_name),
          latest_price = VALUES(latest_price),
          change_rate = VALUES(change_rate),
          related_info_url = VALUES(related_info_url),
          net_value_ratio = VALUES(net_value_ratio),
          holding_shares_10k = VALUES(holding_shares_10k),
          holding_market_value_10k = VALUES(holding_market_value_10k),
          updated_at = CURRENT_TIMESTAMP
    """
    log_write_sql("upsert_stock_holdings", sql, values)
    with connection.cursor() as cursor:
        cursor.executemany(sql, values)
    connection.commit()
    return len(values)


def get_crawl_cursor(connection: Connection, job_name: str, cursor_date: str) -> CrawlCursor | None:
    sql = """
        SELECT job_name, cursor_date, last_fund_code, completed
        FROM fund_crawl_cursor
        WHERE job_name = %s AND cursor_date = %s
    """
    with connection.cursor() as cursor:
        cursor.execute(sql, (job_name, cursor_date))
        row = cursor.fetchone()
    if not row:
        return None
    return CrawlCursor(
        job_name=str(row["job_name"]),
        cursor_date=str(row["cursor_date"]),
        last_fund_code=row["last_fund_code"],
        completed=bool(row["completed"]),
    )


def upsert_crawl_cursor(
    connection: Connection,
    job_name: str,
    cursor_date: str,
    last_fund_code: str | None,
    completed: bool,
) -> None:
    sql = """
        INSERT INTO fund_crawl_cursor (
          job_name,
          cursor_date,
          last_fund_code,
          completed
        )
        VALUES (%s, %s, %s, %s)
        ON DUPLICATE KEY UPDATE
          last_fund_code = VALUES(last_fund_code),
          completed = VALUES(completed),
          updated_at = CURRENT_TIMESTAMP
    """
    values = (job_name, cursor_date, last_fund_code, int(completed))
    log_write_sql("upsert_crawl_cursor", sql, values)
    with connection.cursor() as cursor:
        cursor.execute(sql, values)
    connection.commit()


def fund_profile_recently_updated(connection: Connection, fund_code: str, refresh_days: int) -> bool:
    if refresh_days <= 0:
        return False
    sql = """
        SELECT inception_date, fund_manager, fund_type, management_company, profile_updated_at
        FROM fund_detail
        WHERE fund_code = %s
    """
    with connection.cursor() as cursor:
        cursor.execute(sql, (fund_code,))
        row = cursor.fetchone()
    if not row:
        return False
    if not any(row.get(name) for name in ("inception_date", "fund_manager", "fund_type", "management_company")):
        return False
    return _is_recent_timestamp(row.get("profile_updated_at"), refresh_days)


def fund_table_recently_updated(
    connection: Connection,
    table_name: str,
    fund_code: str,
    refresh_days: int,
) -> bool:
    if refresh_days <= 0:
        return False
    if table_name not in {"fund_feature_data", "fund_rating", "fund_stock_holding"}:
        raise ValueError(f"unsupported fund table for refresh check: {table_name}")
    sql = f"SELECT MAX(updated_at) AS updated_at FROM {table_name} WHERE fund_code = %s"
    with connection.cursor() as cursor:
        cursor.execute(sql, (fund_code,))
        row = cursor.fetchone()
    if not row or row.get("updated_at") is None:
        return False
    return _is_recent_timestamp(row.get("updated_at"), refresh_days)


def fund_data_recently_refreshed(
    connection: Connection,
    data_type: str,
    fund_code: str,
    refresh_days: int,
) -> bool:
    if refresh_days <= 0:
        return False
    _validate_refresh_data_type(data_type)
    sql = """
        SELECT last_success_at
        FROM fund_refresh_state
        WHERE fund_code = %s AND data_type = %s
    """
    with connection.cursor() as cursor:
        cursor.execute(sql, (fund_code, data_type))
        row = cursor.fetchone()
    if not row or row.get("last_success_at") is None:
        return False
    return _is_recent_timestamp(row.get("last_success_at"), refresh_days)


def upsert_fund_refresh_state(connection: Connection, fund_code: str, data_type: str, row_count: int) -> None:
    _validate_refresh_data_type(data_type)
    sql = """
        INSERT INTO fund_refresh_state (
          fund_code,
          data_type,
          last_success_at,
          last_row_count
        )
        VALUES (%s, %s, CURRENT_TIMESTAMP, %s)
        ON DUPLICATE KEY UPDATE
          last_success_at = VALUES(last_success_at),
          last_row_count = VALUES(last_row_count),
          updated_at = CURRENT_TIMESTAMP
    """
    values = (fund_code, data_type, row_count)
    log_write_sql("upsert_fund_refresh_state", sql, values)
    with connection.cursor() as cursor:
        cursor.execute(sql, values)
    connection.commit()


def log_write_sql(operation: str, sql: str, params: Sequence[Any] | Sequence[Sequence[Any]]) -> None:
    if not _env_bool("LOG_SQL", False):
        return

    row_count = _sql_param_count(params)
    logger.info("sql operation=%s rows=%s statement=%s", operation, row_count, _compact_sql(sql))

    if _env_bool("LOG_SQL_PARAMS", False):
        max_params = int(os.getenv("LOG_SQL_MAX_PARAMS", "3"))
        sampled_params = _sample_sql_params(params, max_params)
        omitted = max(0, row_count - len(sampled_params)) if row_count > 1 else 0
        logger.info(
            "sql operation=%s params=%r omitted_rows=%s",
            operation,
            sampled_params,
            omitted,
        )


def _compact_sql(sql: str) -> str:
    return re.sub(r"\s+", " ", sql).strip()


def _sql_param_count(params: Sequence[Any] | Sequence[Sequence[Any]]) -> int:
    if not params:
        return 0
    first = params[0]
    if isinstance(first, (tuple, list)):
        return len(params)
    return 1


def _sample_sql_params(
    params: Sequence[Any] | Sequence[Sequence[Any]],
    max_params: int,
) -> Sequence[Any] | Sequence[Sequence[Any]]:
    if not params:
        return []
    first = params[0]
    if isinstance(first, (tuple, list)):
        return params[:max_params]
    return params


def _env_bool(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "y", "on"}


def _is_recent_timestamp(value: Any, refresh_days: int) -> bool:
    if value is None:
        return False
    if isinstance(value, datetime):
        updated_at = value
    else:
        try:
            updated_at = datetime.fromisoformat(str(value))
        except ValueError:
            return False
    return updated_at >= datetime.now() - timedelta(days=refresh_days)


def _validate_refresh_data_type(data_type: str) -> None:
    if data_type not in {"profile", "nav", "feature", "rating", "holding"}:
        raise ValueError(f"unsupported refresh data type: {data_type}")


def _quote_identifier(value: str) -> str:
    if not re.fullmatch(r"[A-Za-z0-9_]+", value):
        raise ValueError(f"invalid database identifier: {value}")
    return f"`{value}`"


def _ensure_fund_detail_columns(cursor, database: str) -> None:
    columns = {
        "inception_date": "DATE NULL COMMENT '成立日期'",
        "fund_manager": "VARCHAR(255) NULL COMMENT '基金经理'",
        "fund_type": "VARCHAR(100) NULL COMMENT '类型'",
        "management_company": "VARCHAR(255) NULL COMMENT '管理人'",
        "net_asset_scale": "VARCHAR(100) NULL COMMENT '净资产规模'",
        "scale_date": "DATE NULL COMMENT '规模截止至日'",
        "profile_updated_at": "DATETIME NULL COMMENT '基础资料更新时间'",
        "can_buy": "TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否可购买'",
    }
    for column, definition in columns.items():
        cursor.execute(
            """
            SELECT COUNT(*) AS cnt
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = %s
              AND TABLE_NAME = 'fund_detail'
              AND COLUMN_NAME = %s
            """,
            (database, column),
        )
        exists = cursor.fetchone()["cnt"] > 0
        if not exists:
            cursor.execute(f"ALTER TABLE {_quote_identifier(database)}.fund_detail ADD COLUMN {column} {definition}")


def _ensure_fund_stock_holding_columns(cursor, database: str) -> None:
    database_name = _quote_identifier(database)
    cursor.execute(
        """
        SELECT IS_NULLABLE AS is_nullable
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = %s
          AND TABLE_NAME = 'fund_stock_holding'
          AND COLUMN_NAME = 'cutoff_date'
        """,
        (database,),
    )
    column = cursor.fetchone()
    if column is None:
        cursor.execute(
            f"ALTER TABLE {database_name}.fund_stock_holding "
            "ADD COLUMN cutoff_date VARCHAR(8) NULL COMMENT '页面截止至日期' AFTER report_date"
        )
    if column is None or str(column["is_nullable"]).upper() == "YES":
        cursor.execute(
            f"UPDATE {database_name}.fund_stock_holding "
            "SET cutoff_date = report_date WHERE cutoff_date IS NULL OR cutoff_date = ''"
        )
        cursor.execute(
            f"ALTER TABLE {database_name}.fund_stock_holding "
            "MODIFY COLUMN cutoff_date VARCHAR(8) NOT NULL COMMENT '页面截止至日期'"
        )

    cursor.execute(
        """
        SELECT COUNT(*) AS cnt
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = %s
          AND TABLE_NAME = 'fund_stock_holding'
          AND INDEX_NAME = 'idx_fund_stock_cutoff_date'
        """,
        (database,),
    )
    if cursor.fetchone()["cnt"] == 0:
        cursor.execute(
            f"ALTER TABLE {database_name}.fund_stock_holding "
            "ADD KEY idx_fund_stock_cutoff_date (fund_code, cutoff_date)"
        )


def _ensure_portfolio_holding_columns(cursor, database: str) -> None:
    database_name = _quote_identifier(database)
    for table_name in ("fund_holding_import_item", "user_fund_holding"):
        for column_name, _definition in (
            ("holding_cost", "DECIMAL(20,4) NULL COMMENT '持仓成本' AFTER holding_return_rate"),
            ("cost_nav", "DECIMAL(20,6) NULL COMMENT '成本净值' AFTER holding_cost"),
        ):
            cursor.execute(
                """
                SELECT COUNT(*) AS cnt
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = %s
                  AND TABLE_NAME = %s
                  AND COLUMN_NAME = %s
                """,
                (database, table_name, column_name),
            )
            if cursor.fetchone()["cnt"] == 0:
                if column_name == "cost_nav":
                    cursor.execute(
                        f"ALTER TABLE {database_name}.{_quote_identifier(table_name)} "
                        "ADD COLUMN cost_nav DECIMAL(20,6) NULL COMMENT '成本净值' "
                        "AFTER holding_cost"
                    )
                else:
                    cursor.execute(
                        f"ALTER TABLE {database_name}.{_quote_identifier(table_name)} "
                        "ADD COLUMN holding_cost DECIMAL(20,4) NULL COMMENT '持仓成本' "
                        "AFTER holding_return_rate"
                    )
        cursor.execute(
            f"UPDATE {database_name}.{_quote_identifier(table_name)} "
            "SET holding_cost = ROUND(holding_amount - holding_profit, 4) "
            "WHERE holding_cost IS NULL "
            "AND holding_amount IS NOT NULL "
            "AND holding_profit IS NOT NULL "
            "AND holding_amount - holding_profit >= 0"
        )
        cursor.execute(
            f"UPDATE {database_name}.{_quote_identifier(table_name)} "
            "SET cost_nav = ROUND(holding_cost / holding_shares, 6) "
            "WHERE cost_nav IS NULL "
            "AND holding_cost IS NOT NULL "
            "AND holding_shares IS NOT NULL "
            "AND holding_shares > 0"
        )


def _fund_nav_history_ddl(database: str) -> str:
    return f"""
        CREATE TABLE IF NOT EXISTS {database}.fund_nav_history (
          id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
          fund_code VARCHAR(20) NOT NULL COMMENT '基金代码',
          nav_date VARCHAR(8) NOT NULL COMMENT '净值日期',
          unit_nav DECIMAL(18,6) NULL COMMENT '单位净值',
          accumulated_nav DECIMAL(18,6) NULL COMMENT '累计净值',
          daily_growth_rate DECIMAL(10,4) NULL COMMENT '日增长率',
          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
          updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
          PRIMARY KEY (id),
          UNIQUE KEY uk_fund_nav_code_date (fund_code, nav_date),
          KEY idx_fund_nav_date (nav_date)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基金历史净值表'
    """


def _fund_performance_history_ddl(database: str) -> str:
    return f"""
        CREATE TABLE IF NOT EXISTS {database}.fund_performance_history (
          id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
          fund_code VARCHAR(20) NOT NULL COMMENT '基金代码',
          nav_date VARCHAR(8) NOT NULL COMMENT '净值日期',
          fund_name_pinyin VARCHAR(255) NULL COMMENT '基金简称拼音',
          inception_date DATE NULL COMMENT '成立日期',
          weekly_return_rate DECIMAL(14,4) NULL COMMENT '近一周收益率',
          monthly_return_rate DECIMAL(14,4) NULL COMMENT '近一月收益率',
          three_month_return_rate DECIMAL(14,4) NULL COMMENT '近三月收益率',
          six_month_return_rate DECIMAL(14,4) NULL COMMENT '近六月收益率',
          one_year_return_rate DECIMAL(14,4) NULL COMMENT '近一年收益率',
          two_year_return_rate DECIMAL(14,4) NULL COMMENT '近两年收益率',
          three_year_return_rate DECIMAL(14,4) NULL COMMENT '近三年收益率',
          year_to_date_return_rate DECIMAL(14,4) NULL COMMENT '今年以来收益率',
          since_inception_return_rate DECIMAL(14,4) NULL COMMENT '成立以来收益率',
          custom_start_date DATE NOT NULL COMMENT '自定义区间开始日期',
          custom_end_date DATE NOT NULL COMMENT '自定义区间结束日期',
          custom_return_rate DECIMAL(14,4) NULL COMMENT '自定义区间收益率',
          sale_status VARCHAR(10) NULL COMMENT '东方财富销售状态码',
          original_fee_rate DECIMAL(10,4) NULL COMMENT '原手续费率',
          discounted_fee_rate DECIMAL(10,4) NULL COMMENT '折后手续费率',
          discount_factor DECIMAL(10,4) NULL COMMENT '折扣',
          cash_management_fee_rate DECIMAL(10,4) NULL COMMENT '活期宝手续费率',
          source_row TEXT NOT NULL COMMENT '接口原始行',
          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
          updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
          PRIMARY KEY (id),
          UNIQUE KEY uk_fund_performance_code_date (fund_code, nav_date),
          KEY idx_fund_performance_nav_date (nav_date)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基金业绩表现历史表'
    """


def _fund_stock_holding_ddl(database: str) -> str:
    return f"""
        CREATE TABLE IF NOT EXISTS {database}.fund_stock_holding (
          id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
          fund_code VARCHAR(20) NOT NULL COMMENT '基金代码',
          report_period VARCHAR(50) NULL COMMENT '报告期',
          report_date VARCHAR(8) NOT NULL COMMENT '报告期截止日期',
          cutoff_date VARCHAR(8) NOT NULL COMMENT '页面截止至日期',
          rank_no INT NULL COMMENT '序号',
          stock_code VARCHAR(20) NOT NULL COMMENT '股票代码',
          stock_name VARCHAR(100) NULL COMMENT '股票名称',
          latest_price DECIMAL(18,4) NULL COMMENT '最新价',
          change_rate DECIMAL(10,4) NULL COMMENT '涨跌幅',
          related_info_url VARCHAR(255) NULL COMMENT '相关资讯',
          net_value_ratio DECIMAL(10,4) NULL COMMENT '占净值比例',
          holding_shares_10k DECIMAL(20,4) NULL COMMENT '持股数（万股）',
          holding_market_value_10k DECIMAL(20,4) NULL COMMENT '持仓市值（万元）',
          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
          updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
          PRIMARY KEY (id),
          UNIQUE KEY uk_fund_stock_holding (fund_code, report_date, stock_code),
          KEY idx_fund_stock_report_date (report_date),
          KEY idx_fund_stock_cutoff_date (fund_code, cutoff_date),
          KEY idx_fund_stock_code (stock_code)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基金持仓表'
    """


def _fund_holding_import_ddl(database: str) -> str:
    return f"""
        CREATE TABLE IF NOT EXISTS {database}.fund_holding_import (
          id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
          owner_username VARCHAR(64) NOT NULL COMMENT '归属用户',
          source_label VARCHAR(32) NOT NULL COMMENT '来源标识',
          status VARCHAR(32) NOT NULL COMMENT '状态',
          screenshot_date DATE NULL COMMENT '截图日期',
          image_count INT NOT NULL DEFAULT 0 COMMENT '图片数量',
          image_hashes_json JSON NULL COMMENT '图片哈希',
          raw_ocr_json JSON NULL COMMENT '原始OCR结果',
          warnings_json JSON NULL COMMENT '识别告警',
          parser_version VARCHAR(64) NULL COMMENT '解析器版本',
          confirmed_at DATETIME NULL COMMENT '确认时间',
          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
          updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
          PRIMARY KEY (id),
          KEY idx_fund_holding_import_owner_time (owner_username, created_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基金持仓导入批次表'
    """


def _fund_holding_import_item_ddl(database: str) -> str:
    return f"""
        CREATE TABLE IF NOT EXISTS {database}.fund_holding_import_item (
          id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
          import_id BIGINT UNSIGNED NOT NULL COMMENT '导入批次ID',
          row_no INT NOT NULL COMMENT '行号',
          fund_code VARCHAR(20) NULL COMMENT '基金代码',
          fund_name VARCHAR(255) NULL COMMENT '基金名称',
          holding_amount DECIMAL(20,4) NULL COMMENT '持有金额',
          holding_profit DECIMAL(20,4) NULL COMMENT '持有收益',
          holding_return_rate DECIMAL(20,4) NULL COMMENT '持有收益率',
          holding_cost DECIMAL(20,4) NULL COMMENT '持仓成本',
          yesterday_profit DECIMAL(20,4) NULL COMMENT '昨日收益',
          today_profit DECIMAL(20,4) NULL COMMENT '今日收益',
          holding_shares DECIMAL(20,4) NULL COMMENT '持有份额',
          cost_nav DECIMAL(20,6) NULL COMMENT '成本净值',
          screenshot_date DATE NULL COMMENT '截图日期',
          confidence DECIMAL(10,4) NULL COMMENT '识别置信度',
          candidate_json JSON NULL COMMENT '候选基金',
          raw_text_json JSON NULL COMMENT '原始文本',
          status VARCHAR(32) NOT NULL COMMENT '状态',
          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
          updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
          PRIMARY KEY (id),
          KEY idx_fund_holding_import_item_import (import_id),
          KEY idx_fund_holding_import_item_fund_code (fund_code)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基金持仓导入明细表'
    """


def _user_fund_holding_ddl(database: str) -> str:
    return f"""
        CREATE TABLE IF NOT EXISTS {database}.user_fund_holding (
          id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
          owner_username VARCHAR(64) NOT NULL COMMENT '归属用户',
          fund_code VARCHAR(20) NOT NULL COMMENT '基金代码',
          fund_name VARCHAR(255) NOT NULL COMMENT '基金名称',
          holding_amount DECIMAL(20,4) NULL COMMENT '持有金额',
          holding_profit DECIMAL(20,4) NULL COMMENT '持有收益',
          holding_return_rate DECIMAL(20,4) NULL COMMENT '持有收益率',
          holding_cost DECIMAL(20,4) NULL COMMENT '持仓成本',
          yesterday_profit DECIMAL(20,4) NULL COMMENT '昨日收益',
          today_profit DECIMAL(20,4) NULL COMMENT '今日收益',
          holding_shares DECIMAL(20,4) NULL COMMENT '持有份额',
          cost_nav DECIMAL(20,6) NULL COMMENT '成本净值',
          screenshot_date DATE NULL COMMENT '截图日期',
          latest_import_id BIGINT UNSIGNED NULL COMMENT '最近导入批次ID',
          latest_import_at DATETIME NULL COMMENT '最近导入时间',
          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
          updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
          PRIMARY KEY (id),
          UNIQUE KEY uk_user_fund_holding_owner_code (owner_username, fund_code),
          KEY idx_user_fund_holding_owner_time (owner_username, latest_import_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户基金持仓表'
    """


def _fund_feature_data_ddl(database: str) -> str:
    return f"""
        CREATE TABLE IF NOT EXISTS {database}.fund_feature_data (
          id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
          fund_code VARCHAR(20) NOT NULL COMMENT '基金代码',
          period_label VARCHAR(20) NOT NULL COMMENT '统计周期',
          cutoff_date VARCHAR(8) NOT NULL COMMENT '截止日期',
          standard_deviation DECIMAL(10,4) NULL COMMENT '标准差',
          sharpe_ratio DECIMAL(10,4) NULL COMMENT '夏普比率',
          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
          updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
          PRIMARY KEY (id),
          UNIQUE KEY uk_fund_feature_period (fund_code, cutoff_date, period_label),
          KEY idx_fund_feature_cutoff_date (cutoff_date)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基金特色数据表'
    """


def _fund_rating_ddl(database: str) -> str:
    return f"""
        CREATE TABLE IF NOT EXISTS {database}.fund_rating (
          id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
          fund_code VARCHAR(20) NOT NULL COMMENT '基金代码',
          rating_date VARCHAR(8) NOT NULL COMMENT '评级日期',
          zhaoshang_rating TINYINT UNSIGNED NULL COMMENT '招商评级',
          shanghai_rating_3y TINYINT UNSIGNED NULL COMMENT '上海证券三年期评级',
          shanghai_rating_5y TINYINT UNSIGNED NULL COMMENT '上海证券五年期评级',
          jian_rating TINYINT UNSIGNED NULL COMMENT '济安金信评级',
          morning_star_rating TINYINT UNSIGNED NULL COMMENT '晨星评级',
          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
          updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
          PRIMARY KEY (id),
          UNIQUE KEY uk_fund_rating_code_date (fund_code, rating_date),
          KEY idx_fund_rating_date (rating_date)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基金评级表'
    """


def _fund_scale_history_ddl(database: str) -> str:
    return f"""
        CREATE TABLE IF NOT EXISTS {database}.fund_scale_history (
          id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
          fund_code VARCHAR(20) NOT NULL,
          scale_date VARCHAR(8) NOT NULL,
          net_asset_scale_yi DECIMAL(20,4) NULL,
          source_text VARCHAR(100) NULL,
          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
          updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
          PRIMARY KEY (id),
          UNIQUE KEY uk_fund_scale_code_date (fund_code, scale_date),
          KEY idx_fund_scale_date (scale_date)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    """


def _fund_score_profile_ddl(database: str) -> str:
    return f"""
        CREATE TABLE IF NOT EXISTS {database}.fund_score_profile (
          id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
          profile_name VARCHAR(100) NOT NULL,
          version_no INT NOT NULL DEFAULT 1,
          status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
          source_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
          target_months INT NOT NULL DEFAULT 12,
          weights_json JSON NOT NULL,
          calibration_json JSON NULL,
          validation_status VARCHAR(32) NOT NULL DEFAULT 'UNVERIFIED',
          is_active TINYINT(1) NOT NULL DEFAULT 0,
          created_by VARCHAR(64) NULL,
          approved_by VARCHAR(64) NULL,
          approved_at DATETIME NULL,
          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
          updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
          PRIMARY KEY (id),
          UNIQUE KEY uk_fund_score_profile_name_version (profile_name, version_no),
          KEY idx_fund_score_profile_active (is_active, status)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    """


def _fund_score_factor_snapshot_ddl(database: str) -> str:
    return f"""
        CREATE TABLE IF NOT EXISTS {database}.fund_score_factor_snapshot (
          id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
          as_of_date VARCHAR(8) NOT NULL,
          fund_code VARCHAR(20) NOT NULL,
          fund_type VARCHAR(100) NULL,
          comparison_group VARCHAR(100) NULL,
          factors_json JSON NOT NULL,
          normalized_json JSON NULL,
          data_coverage DECIMAL(8,6) NOT NULL DEFAULT 0,
          forward_return DECIMAL(18,8) NULL,
          profitable TINYINT(1) NULL,
          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
          updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
          PRIMARY KEY (id),
          UNIQUE KEY uk_fund_score_factor_date_code (as_of_date, fund_code),
          KEY idx_fund_score_factor_code_date (fund_code, as_of_date),
          KEY idx_fund_score_factor_group_date (comparison_group, as_of_date)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    """


def _fund_score_result_ddl(database: str) -> str:
    return f"""
        CREATE TABLE IF NOT EXISTS {database}.fund_score_result (
          id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
          profile_id BIGINT UNSIGNED NOT NULL,
          fund_code VARCHAR(20) NOT NULL,
          as_of_date VARCHAR(8) NOT NULL,
          total_score DECIMAL(8,4) NULL,
          profit_probability DECIMAL(8,6) NULL,
          confidence VARCHAR(16) NOT NULL DEFAULT 'LOW',
          data_coverage DECIMAL(8,6) NOT NULL DEFAULT 0,
          comparison_group VARCHAR(100) NULL,
          category_rank INT NULL,
          category_count INT NULL,
          components_json JSON NULL,
          methodology_version VARCHAR(32) NOT NULL DEFAULT 'fund-score-v1',
          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
          updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
          PRIMARY KEY (id),
          UNIQUE KEY uk_fund_score_result_profile_code_date (profile_id, fund_code, as_of_date),
          KEY idx_fund_score_result_active_list (profile_id, as_of_date, total_score),
          KEY idx_fund_score_result_code_date (fund_code, as_of_date)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    """


def _fund_score_backtest_ddl(database: str) -> str:
    return f"""
        CREATE TABLE IF NOT EXISTS {database}.fund_score_backtest (
          id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
          profile_id BIGINT UNSIGNED NOT NULL,
          train_start_date VARCHAR(8) NULL,
          train_end_date VARCHAR(8) NULL,
          test_start_date VARCHAR(8) NULL,
          test_end_date VARCHAR(8) NULL,
          sample_count INT NOT NULL DEFAULT 0,
          fold_count INT NOT NULL DEFAULT 0,
          auc DECIMAL(10,6) NULL,
          brier_score DECIMAL(10,6) NULL,
          baseline_brier_score DECIMAL(10,6) NULL,
          top20_win_rate DECIMAL(10,6) NULL,
          baseline_win_rate DECIMAL(10,6) NULL,
          win_rate_lift DECIMAL(10,6) NULL,
          passed TINYINT(1) NOT NULL DEFAULT 0,
          limitations_json JSON NULL,
          metrics_json JSON NULL,
          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
          PRIMARY KEY (id),
          KEY idx_fund_score_backtest_profile_time (profile_id, created_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    """


def _fund_score_job_ddl(database: str) -> str:
    return f"""
        CREATE TABLE IF NOT EXISTS {database}.fund_score_job (
          id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
          job_type VARCHAR(32) NOT NULL,
          profile_id BIGINT UNSIGNED NULL,
          status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
          requested_by VARCHAR(64) NULL,
          message VARCHAR(1000) NULL,
          started_at DATETIME NULL,
          finished_at DATETIME NULL,
          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
          updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
          PRIMARY KEY (id),
          KEY idx_fund_score_job_status_time (status, created_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    """


def _ensure_default_score_profile(cursor: Any) -> None:
    cursor.execute("SELECT COUNT(*) AS count_value FROM fund_score_profile")
    row = cursor.fetchone()
    if row and int(row["count_value"]) > 0:
        return
    weights = (
        '{"decline_today":4,"decline_1d":6,"decline_1w":6,"decline_2w":6,'
        '"decline_3w":6,"decline_4w":5,'
        '"return_3m":3,"return_6m":4,'
        '"return_1y":5,"return_2y":4,"return_3y":4,'
        '"volatility_1y":4,"volatility_3y":5,'
        '"sharpe_1y":5,"sharpe_3y":5,'
        '"drawdown_1y":4,"drawdown_3y":4,'
        '"rating_zhaoshang":3,"rating_shanghai_3y":4,'
        '"rating_shanghai_5y":3,"rating_jian":3,'
        '"rating_morningstar":4,"scale":3}'
    )
    cursor.execute(
        """
        INSERT INTO fund_score_profile (
          profile_name, version_no, status, source_type, target_months,
          weights_json, validation_status, is_active, created_by
        )
        VALUES ('保守初始权重', 1, 'ACTIVE', 'SEED', 12, %s, 'UNVERIFIED', 1, 'system')
        """,
        (weights,),
    )


def _fund_refresh_state_ddl(database: str) -> str:
    return f"""
        CREATE TABLE IF NOT EXISTS {database}.fund_refresh_state (
          id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
          fund_code VARCHAR(20) NOT NULL COMMENT '基金代码',
          data_type VARCHAR(20) NOT NULL COMMENT '数据类型',
          last_success_at DATETIME NOT NULL COMMENT '最近成功刷新时间',
          last_row_count INT NOT NULL DEFAULT 0 COMMENT '最近刷新行数',
          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
          updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
          PRIMARY KEY (id),
          UNIQUE KEY uk_fund_refresh_state_code_type (fund_code, data_type),
          KEY idx_fund_refresh_state_type_time (data_type, last_success_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基金数据刷新状态表'
    """


def _yangjibao_news_ddl(database: str) -> str:
    return f"""
        CREATE TABLE IF NOT EXISTS {database}.yangjibao_news (
          id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
          news_id VARCHAR(32) NOT NULL COMMENT '养基宝资讯ID',
          title VARCHAR(500) NULL COMMENT '标题',
          content TEXT NOT NULL COMMENT '正文',
          display_time DATETIME NOT NULL COMMENT '展示时间',
          images_json JSON NULL COMMENT '图片列表',
          score INT NULL COMMENT '重要级别',
          news_type INT NULL COMMENT '资讯类型',
          source_json JSON NOT NULL COMMENT '接口原始数据',
          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
          updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
          PRIMARY KEY (id),
          UNIQUE KEY uk_yangjibao_news_id (news_id),
          KEY idx_yangjibao_news_display_time (display_time),
          KEY idx_yangjibao_news_score (score)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='养基宝资讯表'
    """


def _sina_finance_news_ddl(database: str) -> str:
    return f"""
      CREATE TABLE IF NOT EXISTS {database}.sina_finance_news (
        id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, news_id VARCHAR(32) NOT NULL,
        category_tag INT NOT NULL DEFAULT 0, category_name VARCHAR(50) NOT NULL DEFAULT '全部',
        content TEXT NOT NULL, create_time DATETIME NOT NULL, source_update_time DATETIME NOT NULL,
        doc_url VARCHAR(1000) NULL, tags_json JSON NULL, images_json JSON NULL, source_json JSON NOT NULL,
        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        PRIMARY KEY (id), UNIQUE KEY uk_sina_finance_news_id (news_id),
        KEY idx_sina_finance_news_category_time (category_tag, create_time), KEY idx_sina_finance_news_time (create_time)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='新浪财经7x24资讯表'
    """


def _stock_detail_ddl(database: str) -> str:
    return f"""
      CREATE TABLE IF NOT EXISTS {database}.stock_detail (
        id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, stock_code VARCHAR(20) NOT NULL,
        stock_name VARCHAR(100) NOT NULL, market_code INT NOT NULL, exchange_name VARCHAR(20) NOT NULL,
        listing_date DATE NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        PRIMARY KEY (id), UNIQUE KEY uk_stock_detail_code (stock_code),
        KEY idx_stock_detail_name (stock_name), KEY idx_stock_detail_market (market_code)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='股票基础信息表'
    """


def _stock_daily_history_ddl(database: str) -> str:
    return f"""
      CREATE TABLE IF NOT EXISTS {database}.stock_daily_history (
        id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, stock_code VARCHAR(20) NOT NULL, trade_date DATE NOT NULL,
        quote_time DATETIME NULL, latest_price DECIMAL(20,4) NULL, change_rate DECIMAL(12,4) NULL,
        change_amount DECIMAL(20,4) NULL, volume BIGINT NULL, amount DECIMAL(24,4) NULL,
        amplitude DECIMAL(12,4) NULL, turnover_rate DECIMAL(12,4) NULL, pe_dynamic DECIMAL(20,4) NULL,
        volume_ratio DECIMAL(12,4) NULL, five_min_change_rate DECIMAL(12,4) NULL,
        high_price DECIMAL(20,4) NULL, low_price DECIMAL(20,4) NULL, open_price DECIMAL(20,4) NULL,
        previous_close DECIMAL(20,4) NULL, total_market_cap DECIMAL(24,4) NULL,
        float_market_cap DECIMAL(24,4) NULL, speed_rate DECIMAL(12,4) NULL, pb_ratio DECIMAL(20,4) NULL,
        change_rate_60d DECIMAL(12,4) NULL, change_rate_ytd DECIMAL(12,4) NULL,
        main_net_inflow DECIMAL(24,4) NULL, pe_ttm DECIMAL(20,4) NULL, raw_json JSON NOT NULL,
        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        `comment` VARCHAR(500) NULL COMMENT '备注',
        PRIMARY KEY (id), UNIQUE KEY uk_stock_daily (stock_code,trade_date),
        KEY idx_stock_daily_date (trade_date), KEY idx_stock_daily_date_change (trade_date,change_rate)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='股票每日行情表'
    """


def _ensure_stock_daily_history_columns(cursor: Any, database: str) -> None:
    cursor.execute(
        """
        SELECT COUNT(*) AS cnt
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = %s
          AND TABLE_NAME = 'stock_daily_history'
          AND COLUMN_NAME = 'comment'
        """,
        (database,),
    )
    if cursor.fetchone()["cnt"] == 0:
        cursor.execute(
            f"ALTER TABLE {_quote_identifier(database)}.stock_daily_history "
            "ADD COLUMN `comment` VARCHAR(500) NULL COMMENT '备注' AFTER updated_at"
        )


def _ensure_sina_finance_news_columns(cursor: Any, database: str) -> None:
    cursor.execute(f"USE {_quote_identifier(database)}")
    cursor.execute(
        "SELECT column_name FROM information_schema.columns WHERE table_schema=%s AND table_name='sina_finance_news'",
        (database,),
    )
    columns = {row["column_name"] for row in cursor.fetchall()}
    if "category_tag" not in columns:
        cursor.execute(f"ALTER TABLE {_quote_identifier(database)}.sina_finance_news ADD COLUMN category_tag INT NOT NULL DEFAULT 0 AFTER news_id")
    if "category_name" not in columns:
        cursor.execute(f"ALTER TABLE {_quote_identifier(database)}.sina_finance_news ADD COLUMN category_name VARCHAR(50) NOT NULL DEFAULT '全部' AFTER category_tag")
    cursor.execute(
        "SELECT index_name FROM information_schema.statistics WHERE table_schema=%s AND table_name='sina_finance_news'",
        (database,),
    )
    indexes = {row["index_name"] for row in cursor.fetchall()}
    if "uk_sina_finance_news_category" in indexes:
        cursor.execute("DELETE newer FROM sina_finance_news newer JOIN sina_finance_news older ON newer.news_id=older.news_id AND newer.id>older.id")
        cursor.execute(f"ALTER TABLE {_quote_identifier(database)}.sina_finance_news DROP INDEX uk_sina_finance_news_category")
    if "uk_sina_finance_news_id" not in indexes:
        cursor.execute(f"ALTER TABLE {_quote_identifier(database)}.sina_finance_news ADD UNIQUE KEY uk_sina_finance_news_id (news_id)")
    if "idx_sina_finance_news_category_time" not in indexes:
        cursor.execute(f"ALTER TABLE {_quote_identifier(database)}.sina_finance_news ADD KEY idx_sina_finance_news_category_time (category_tag, create_time)")


def _fund_crawl_cursor_ddl(database: str) -> str:
    return f"""
        CREATE TABLE IF NOT EXISTS {database}.fund_crawl_cursor (
          id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
          job_name VARCHAR(50) NOT NULL COMMENT '任务名称',
          cursor_date VARCHAR(8) NOT NULL COMMENT '游标日期',
          last_fund_code VARCHAR(20) NULL COMMENT '最后成功基金代码',
          completed TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否完成',
          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
          updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
          PRIMARY KEY (id),
          UNIQUE KEY uk_fund_crawl_cursor_job_date (job_name, cursor_date),
          KEY idx_fund_crawl_cursor_date (cursor_date)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基金抓取游标表'
    """
