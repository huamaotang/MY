from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timedelta
import logging
import os
import re
from typing import Any, Iterable, Sequence

import pymysql
from pymysql.connections import Connection


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
            cursor.execute(
                f"""
                CREATE TABLE IF NOT EXISTS {database}.cfg_fund (
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
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_cfg_fund_code (fund_code)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基金配置表'
                """
            )
            _ensure_cfg_fund_columns(cursor, config.database)
            cursor.execute(_fund_nav_history_ddl(database))
            cursor.execute(_fund_stock_holding_ddl(database))
            cursor.execute(_fund_feature_data_ddl(database))
            cursor.execute(_fund_rating_ddl(database))
            cursor.execute(_fund_refresh_state_ddl(database))
            cursor.execute(_fund_crawl_cursor_ddl(database))
        connection.commit()
    finally:
        connection.close()


def upsert_funds(connection: Connection, funds: Iterable[tuple[str, str]]) -> int:
    rows = list(funds)
    if not rows:
        return 0

    sql = """
        INSERT INTO cfg_fund (fund_code, fund_name)
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


def list_fund_codes(
    connection: Connection,
    limit: int | None = None,
    offset: int = 0,
    start_fund_code: str | None = None,
    after_fund_code: str | None = None,
) -> list[str]:
    sql = "SELECT fund_code FROM cfg_fund"
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
    with connection.cursor() as cursor:
        cursor.execute(sql, tuple(params))
        rows = cursor.fetchall()
    return [str(row["fund_code"]) for row in rows]


def update_fund_profile(connection: Connection, profile: FundProfile) -> int:
    sql = """
        UPDATE cfg_fund
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
    with connection.cursor() as cursor:
        affected = cursor.execute(sql, values)
    connection.commit()
    return affected


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
    with connection.cursor() as cursor:
        cursor.executemany(sql, values)
    connection.commit()
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


def upsert_stock_holdings(connection: Connection, rows: Iterable[FundStockHolding]) -> int:
    values = [
        (
            row.fund_code,
            row.report_period,
            row.report_date,
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
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        ON DUPLICATE KEY UPDATE
          report_period = VALUES(report_period),
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
        FROM cfg_fund
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
        logger.info("sql operation=%s params_sample=%r", operation, _sample_sql_params(params, max_params))


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


def _ensure_cfg_fund_columns(cursor, database: str) -> None:
    columns = {
        "inception_date": "DATE NULL COMMENT '成立日期'",
        "fund_manager": "VARCHAR(255) NULL COMMENT '基金经理'",
        "fund_type": "VARCHAR(100) NULL COMMENT '类型'",
        "management_company": "VARCHAR(255) NULL COMMENT '管理人'",
        "net_asset_scale": "VARCHAR(100) NULL COMMENT '净资产规模'",
        "scale_date": "DATE NULL COMMENT '规模截止至日'",
        "profile_updated_at": "DATETIME NULL COMMENT '基础资料更新时间'",
    }
    for column, definition in columns.items():
        cursor.execute(
            """
            SELECT COUNT(*) AS cnt
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = %s
              AND TABLE_NAME = 'cfg_fund'
              AND COLUMN_NAME = %s
            """,
            (database, column),
        )
        exists = cursor.fetchone()["cnt"] > 0
        if not exists:
            cursor.execute(f"ALTER TABLE {_quote_identifier(database)}.cfg_fund ADD COLUMN {column} {definition}")


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


def _fund_stock_holding_ddl(database: str) -> str:
    return f"""
        CREATE TABLE IF NOT EXISTS {database}.fund_stock_holding (
          id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
          fund_code VARCHAR(20) NOT NULL COMMENT '基金代码',
          report_period VARCHAR(50) NULL COMMENT '报告期',
          report_date VARCHAR(8) NOT NULL COMMENT '报告期截止日期',
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
          KEY idx_fund_stock_code (stock_code)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基金持仓表'
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
