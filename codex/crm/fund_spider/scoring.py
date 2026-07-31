from __future__ import annotations

import bisect
import json
import math
import random
import statistics
from collections import defaultdict
from dataclasses import dataclass
from datetime import date, datetime, timedelta
from typing import Any, Iterable

from pymysql.cursors import SSDictCursor

from db import DatabaseConfig, connect, database_config_from_env, ensure_schema


METHODOLOGY_VERSION = "fund-score-v1"
MIN_CATEGORY_SIZE = 30
MIN_SCORE_COVERAGE = 0.70

DEFAULT_WEIGHTS: dict[str, int] = {
    "return_1m": 1,
    "return_3m": 3,
    "return_6m": 5,
    "return_1y": 7,
    "return_2y": 5,
    "return_3y": 4,
    "volatility_1y": 5,
    "volatility_3y": 10,
    "sharpe_1y": 10,
    "sharpe_3y": 15,
    "drawdown_1y": 8,
    "drawdown_3y": 12,
    "rating_zhaoshang": 2,
    "rating_shanghai_3y": 2,
    "rating_shanghai_5y": 1,
    "rating_jian": 2,
    "rating_morningstar": 3,
    "scale": 5,
}

FACTOR_LABELS: dict[str, str] = {
    "return_1m": "近1月收益",
    "return_3m": "近3月收益",
    "return_6m": "近6月收益",
    "return_1y": "近1年收益",
    "return_2y": "近2年收益",
    "return_3y": "近3年收益",
    "volatility_1y": "近1年标准差",
    "volatility_3y": "近3年标准差",
    "sharpe_1y": "近1年夏普",
    "sharpe_3y": "近3年夏普",
    "drawdown_1y": "近1年最大回撤",
    "drawdown_3y": "近3年最大回撤",
    "rating_zhaoshang": "招商评级",
    "rating_shanghai_3y": "上海3年评级",
    "rating_shanghai_5y": "上海5年评级",
    "rating_jian": "济安评级",
    "rating_morningstar": "晨星评级",
    "scale": "基金规模",
}

INVERSE_FACTORS = {
    "volatility_1y",
    "volatility_3y",
    "drawdown_1y",
    "drawdown_3y",
}

BLOCKS: dict[str, tuple[list[str], tuple[int, int]]] = {
    "returns": (
        ["return_1m", "return_3m", "return_6m", "return_1y", "return_2y", "return_3y"],
        (10, 40),
    ),
    "volatility": (["volatility_1y", "volatility_3y"], (10, 30)),
    "sharpe": (["sharpe_1y", "sharpe_3y"], (15, 35)),
    "drawdown": (["drawdown_1y", "drawdown_3y"], (10, 30)),
    "rating": (
        [
            "rating_zhaoshang",
            "rating_shanghai_3y",
            "rating_shanghai_5y",
            "rating_jian",
            "rating_morningstar",
        ],
        (0, 15),
    ),
    "scale": (["scale"], (0, 10)),
}


@dataclass(frozen=True)
class NavPoint:
    nav_date: date
    value: float | None
    daily_return: float | None


def parse_compact_date(value: str) -> date:
    return datetime.strptime(value, "%Y%m%d").date()


def compact_date(value: date) -> str:
    return value.strftime("%Y%m%d")


def parse_number(value: Any) -> float | None:
    if value is None:
        return None
    try:
        parsed = float(str(value).replace("%", "").replace(",", "").strip())
    except (TypeError, ValueError):
        return None
    return parsed if math.isfinite(parsed) else None


def parse_scale_yi(value: Any) -> float | None:
    if value is None:
        return None
    text = str(value).replace(",", "").strip()
    digits = ""
    for character in text:
        if character.isdigit() or character in ".-":
            digits += character
        elif digits:
            break
    parsed = parse_number(digits)
    if parsed is None:
        return None
    if "万亿元" in text:
        return parsed * 10000
    if "万元" in text:
        return parsed / 10000
    if text.endswith("元") and "亿元" not in text:
        return parsed / 100000000
    return parsed


def parent_group(fund_type: str) -> str:
    text = fund_type.strip()
    if not text:
        return ""
    if text.startswith("QDII"):
        return "QDII"
    return text.split("-", 1)[0]


def _target_index(points: list[NavPoint], target: date, end_index: int) -> int | None:
    dates = [point.nav_date for point in points[: end_index + 1]]
    index = bisect.bisect_right(dates, target) - 1
    if index < 0:
        return None
    if abs((dates[index] - target).days) > 10:
        return None
    return index


def _window(points: list[NavPoint], end_index: int, days: int) -> list[NavPoint]:
    start = points[end_index].nav_date - timedelta(days=days)
    dates = [point.nav_date for point in points[: end_index + 1]]
    index = bisect.bisect_left(dates, start)
    return points[index : end_index + 1]


def _return_between(start: NavPoint, end: NavPoint) -> float | None:
    if start.value is None or end.value is None or start.value <= 0:
        return None
    return (end.value / start.value - 1.0) * 100.0


def _daily_returns(points: list[NavPoint]) -> list[float]:
    values: list[float] = []
    previous: float | None = None
    for point in points:
        value = point.daily_return
        if value is None and previous is not None and point.value is not None and previous > 0:
            value = point.value / previous - 1.0
        if value is not None and math.isfinite(value):
            values.append(value)
        if point.value is not None:
            previous = point.value
    return values


def _risk_metrics(points: list[NavPoint]) -> tuple[float | None, float | None, float | None]:
    returns = _daily_returns(points)
    if len(returns) < 30:
        return None, None, None
    deviation = statistics.stdev(returns)
    volatility = deviation * math.sqrt(250) * 100.0
    sharpe = statistics.mean(returns) / deviation * math.sqrt(250) if deviation > 0 else None
    peak: float | None = None
    maximum_drawdown = 0.0
    for point in points:
        if point.value is None:
            continue
        peak = point.value if peak is None else max(peak, point.value)
        if peak > 0:
            maximum_drawdown = max(maximum_drawdown, (peak - point.value) / peak * 100.0)
    return volatility, sharpe, maximum_drawdown


def calculate_nav_factors(points: list[NavPoint], end_index: int | None = None) -> dict[str, float | None]:
    if not points:
        return {}
    index = len(points) - 1 if end_index is None else end_index
    end = points[index]
    factors: dict[str, float | None] = {}
    periods = {
        "return_1m": 30,
        "return_3m": 91,
        "return_6m": 182,
        "return_1y": 365,
        "return_2y": 730,
        "return_3y": 1095,
    }
    for key, days in periods.items():
        start_index = _target_index(points, end.nav_date - timedelta(days=days), index)
        factors[key] = None if start_index is None else _return_between(points[start_index], end)
    for suffix, days in (("1y", 365), ("3y", 1095)):
        volatility, sharpe, drawdown = _risk_metrics(_window(points, index, days))
        factors[f"volatility_{suffix}"] = volatility
        factors[f"sharpe_{suffix}"] = sharpe
        factors[f"drawdown_{suffix}"] = drawdown
    return factors


def percentile_scores(rows: list[dict[str, Any]], factor: str) -> dict[str, float]:
    pairs = [
        (float(row["factors"][factor]), row["fund_code"])
        for row in rows
        if row["factors"].get(factor) is not None
    ]
    if not pairs:
        return {}
    ordered = sorted(value for value, _ in pairs)
    count = len(ordered)
    result: dict[str, float] = {}
    for value, fund_code in pairs:
        left = bisect.bisect_left(ordered, value)
        right = bisect.bisect_right(ordered, value)
        rank = (left + right - 1) / 2.0
        percentile = 50.0 if count == 1 else rank / (count - 1) * 100.0
        if factor in INVERSE_FACTORS:
            percentile = 100.0 - percentile
        if factor == "scale":
            percentile = min(100.0, percentile / 0.8)
        result[fund_code] = round(percentile, 6)
    return result


def validate_weights(weights: dict[str, Any]) -> dict[str, int]:
    if set(weights) != set(DEFAULT_WEIGHTS):
        missing = sorted(set(DEFAULT_WEIGHTS) - set(weights))
        extra = sorted(set(weights) - set(DEFAULT_WEIGHTS))
        raise ValueError(f"invalid factor keys, missing={missing}, extra={extra}")
    normalized: dict[str, int] = {}
    for key, value in weights.items():
        parsed = int(value)
        if parsed < 0 or parsed > 100:
            raise ValueError(f"weight {key} must be between 0 and 100")
        normalized[key] = parsed
    if sum(normalized.values()) != 100:
        raise ValueError("weights must total 100")
    return normalized


def score_factors(
    normalized: dict[str, float | None],
    raw: dict[str, float | None],
    weights: dict[str, int],
) -> tuple[float | None, float, list[dict[str, Any]]]:
    available_weight = sum(weight for key, weight in weights.items() if normalized.get(key) is not None)
    coverage = available_weight / 100.0
    if coverage < MIN_SCORE_COVERAGE or raw.get("return_1y") is None:
        return None, coverage, []
    total = 0.0
    components: list[dict[str, Any]] = []
    for key, weight in weights.items():
        value = normalized.get(key)
        if value is None or weight <= 0:
            continue
        effective_weight = weight / coverage
        contribution = value * effective_weight / 100.0
        total += contribution
        components.append(
            {
                "factorKey": key,
                "label": FACTOR_LABELS[key],
                "rawValue": raw.get(key),
                "normalizedScore": round(value, 4),
                "weight": weight,
                "effectiveWeight": round(effective_weight, 4),
                "contribution": round(contribution, 4),
            }
        )
    return round(total, 4), coverage, components


def _latest_rows(connection, table: str, date_column: str) -> dict[str, dict[str, Any]]:
    sql = f"""
        SELECT value_row.*
        FROM {table} value_row
        JOIN (
          SELECT fund_code, MAX({date_column}) AS max_date
          FROM {table}
          GROUP BY fund_code
        ) latest
          ON latest.fund_code = value_row.fund_code
         AND latest.max_date = value_row.{date_column}
    """
    with connection.cursor() as cursor:
        cursor.execute(sql)
        return {str(row["fund_code"]): row for row in cursor.fetchall()}


def _latest_features(connection) -> dict[str, dict[str, dict[str, Any]]]:
    sql = """
        SELECT feature.*
        FROM fund_feature_data feature
        JOIN (
          SELECT fund_code, period_label, MAX(cutoff_date) AS max_date
          FROM fund_feature_data
          GROUP BY fund_code, period_label
        ) latest
          ON latest.fund_code = feature.fund_code
         AND latest.period_label = feature.period_label
         AND latest.max_date = feature.cutoff_date
    """
    result: dict[str, dict[str, dict[str, Any]]] = defaultdict(dict)
    with connection.cursor() as cursor:
        cursor.execute(sql)
        for row in cursor.fetchall():
            result[str(row["fund_code"])][str(row["period_label"])] = row
    return result


def _load_nav_factors(connection, as_of: date) -> dict[str, dict[str, float | None]]:
    start_date = compact_date(as_of - timedelta(days=1110))
    end_date = compact_date(as_of)
    factors: dict[str, dict[str, float | None]] = {}
    cursor = connection.cursor(SSDictCursor)
    try:
        cursor.execute(
            """
            SELECT fund_code, nav_date, unit_nav, accumulated_nav, daily_growth_rate
            FROM fund_nav_history
            WHERE nav_date BETWEEN %s AND %s
            ORDER BY fund_code, nav_date
            """,
            (start_date, end_date),
        )
        current_code: str | None = None
        points: list[NavPoint] = []
        for row in cursor:
            code = str(row["fund_code"])
            if current_code is not None and code != current_code:
                factors[current_code] = calculate_nav_factors(points)
                points = []
            current_code = code
            accumulated = parse_number(row["accumulated_nav"])
            unit = parse_number(row["unit_nav"])
            daily = parse_number(row["daily_growth_rate"])
            points.append(
                NavPoint(
                    parse_compact_date(str(row["nav_date"])),
                    accumulated if accumulated is not None else unit,
                    None if daily is None else daily / 100.0,
                )
            )
        if current_code is not None:
            factors[current_code] = calculate_nav_factors(points)
    finally:
        cursor.close()
    return factors


def _latest_rows_as_of(
    connection,
    table: str,
    date_column: str,
    as_of_text: str,
) -> dict[str, dict[str, Any]]:
    sql = f"""
        SELECT value_row.*
        FROM {table} value_row
        JOIN (
          SELECT fund_code, MAX({date_column}) AS max_date
          FROM {table}
          WHERE {date_column} <= %s
          GROUP BY fund_code
        ) latest
          ON latest.fund_code = value_row.fund_code
         AND latest.max_date = value_row.{date_column}
    """
    with connection.cursor() as cursor:
        cursor.execute(sql, (as_of_text,))
        return {str(row["fund_code"]): row for row in cursor.fetchall()}


def _load_forward_returns(connection, as_of: date) -> dict[str, float]:
    target = as_of + timedelta(days=365)
    start_text = compact_date(as_of - timedelta(days=10))
    end_text = compact_date(target + timedelta(days=10))
    result: dict[str, float] = {}
    cursor = connection.cursor(SSDictCursor)
    try:
        cursor.execute(
            """
            SELECT fund_code, nav_date, unit_nav, accumulated_nav
            FROM fund_nav_history
            WHERE nav_date BETWEEN %s AND %s
            ORDER BY fund_code, nav_date
            """,
            (start_text, end_text),
        )
        current_code: str | None = None
        points: list[NavPoint] = []

        def save_forward_return(fund_code: str | None, values: list[NavPoint]) -> None:
            if fund_code is None or not values:
                return
            start_candidates = [point for point in values if point.nav_date <= as_of]
            target_candidates = [point for point in values if point.nav_date <= target]
            if not start_candidates or not target_candidates:
                return
            start_point = start_candidates[-1]
            target_point = target_candidates[-1]
            if abs((start_point.nav_date - as_of).days) > 10:
                return
            if abs((target_point.nav_date - target).days) > 10:
                return
            forward_return = _return_between(start_point, target_point)
            if forward_return is not None:
                result[fund_code] = forward_return

        for row in cursor:
            code = str(row["fund_code"])
            if current_code is not None and code != current_code:
                save_forward_return(current_code, points)
                points = []
            current_code = code
            accumulated = parse_number(row["accumulated_nav"])
            unit = parse_number(row["unit_nav"])
            points.append(
                NavPoint(
                    parse_compact_date(str(row["nav_date"])),
                    accumulated if accumulated is not None else unit,
                    None,
                )
            )
        save_forward_return(current_code, points)
    finally:
        cursor.close()
    return result


def _normalize_snapshot_rows(rows: list[dict[str, Any]]) -> None:
    exact_groups: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for row in rows:
        exact_groups[row["fund_type"]].append(row)
    comparison_groups: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for row in rows:
        group = row["fund_type"]
        if len(exact_groups[group]) < MIN_CATEGORY_SIZE:
            group = parent_group(group)
        row["comparison_group"] = group
        comparison_groups[group].append(row)

    for group_rows in comparison_groups.values():
        for factor in DEFAULT_WEIGHTS:
            if factor.startswith("rating_"):
                for row in group_rows:
                    star = row["factors"].get(factor)
                    if star is not None:
                        row["normalized"][factor] = max(0.0, min(100.0, (star - 1.0) * 25.0))
                continue
            scores = percentile_scores(group_rows, factor)
            for row in group_rows:
                if row["fund_code"] in scores:
                    row["normalized"][factor] = scores[row["fund_code"]]


def build_historical_factor_snapshot(
    as_of_text: str,
    db_config: DatabaseConfig | None = None,
) -> int:
    """Build one point-in-time snapshot without using observations after as_of_text."""
    config = db_config or database_config_from_env()
    ensure_schema(config)
    as_of = parse_compact_date(as_of_text)
    connection = connect(config)
    try:
        with connection.cursor() as cursor:
            cursor.execute(
                """
                SELECT fund_code, fund_type
                FROM fund_detail
                WHERE fund_type IS NOT NULL AND fund_type <> ''
                  AND (inception_date IS NULL OR inception_date <= %s)
                """,
                (as_of.isoformat(),),
            )
            profiles = {str(row["fund_code"]): row for row in cursor.fetchall()}
        nav_factors = _load_nav_factors(connection, as_of)
        ratings = _latest_rows_as_of(connection, "fund_rating", "rating_date", as_of_text)
        scales = _latest_rows_as_of(connection, "fund_scale_history", "scale_date", as_of_text)
        forward_returns = _load_forward_returns(connection, as_of)

        rows: list[dict[str, Any]] = []
        for fund_code, profile in profiles.items():
            factors = dict(nav_factors.get(fund_code, {}))
            if not factors:
                continue
            rating = ratings.get(fund_code, {})
            scale = scales.get(fund_code, {})
            factors.update(
                {
                    "rating_zhaoshang": parse_number(rating.get("zhaoshang_rating")),
                    "rating_shanghai_3y": parse_number(rating.get("shanghai_rating_3y")),
                    "rating_shanghai_5y": parse_number(rating.get("shanghai_rating_5y")),
                    "rating_jian": parse_number(rating.get("jian_rating")),
                    "rating_morningstar": parse_number(rating.get("morning_star_rating")),
                    "scale": parse_number(scale.get("net_asset_scale_yi")),
                }
            )
            rows.append(
                {
                    "fund_code": fund_code,
                    "fund_type": str(profile["fund_type"]),
                    "comparison_group": str(profile["fund_type"]),
                    "factors": factors,
                    "normalized": {},
                }
            )
        _normalize_snapshot_rows(rows)
        values = []
        for row in rows:
            available = sum(
                DEFAULT_WEIGHTS[key]
                for key in DEFAULT_WEIGHTS
                if row["normalized"].get(key) is not None
            )
            forward_return = forward_returns.get(row["fund_code"])
            values.append(
                (
                    as_of_text,
                    row["fund_code"],
                    row["fund_type"],
                    row["comparison_group"],
                    json.dumps(row["factors"], ensure_ascii=False),
                    json.dumps(row["normalized"], ensure_ascii=False),
                    available / 100.0,
                    forward_return,
                    None if forward_return is None else int(forward_return > 0),
                )
            )
        with connection.cursor() as cursor:
            cursor.executemany(
                """
                INSERT INTO fund_score_factor_snapshot (
                  as_of_date, fund_code, fund_type, comparison_group,
                  factors_json, normalized_json, data_coverage,
                  forward_return, profitable
                )
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
                ON DUPLICATE KEY UPDATE
                  fund_type = VALUES(fund_type),
                  comparison_group = VALUES(comparison_group),
                  factors_json = VALUES(factors_json),
                  normalized_json = VALUES(normalized_json),
                  data_coverage = VALUES(data_coverage),
                  forward_return = VALUES(forward_return),
                  profitable = VALUES(profitable),
                  updated_at = CURRENT_TIMESTAMP
                """,
                values,
            )
        connection.commit()
        return len(values)
    except Exception:
        connection.rollback()
        raise
    finally:
        connection.close()


def build_historical_factor_snapshots(
    start_date: str,
    end_date: str | None = None,
    step_months: int = 1,
    db_config: DatabaseConfig | None = None,
) -> tuple[int, int]:
    if step_months < 1:
        raise ValueError("step_months must be at least 1")
    config = db_config or database_config_from_env()
    ensure_schema(config)
    connection = connect(config)
    try:
        with connection.cursor() as cursor:
            cursor.execute("SELECT MAX(nav_date) AS max_date FROM fund_nav_history")
            max_date = cursor.fetchone()["max_date"]
            if not max_date:
                return 0, 0
            maximum_labeled_date = compact_date(parse_compact_date(str(max_date)) - timedelta(days=365))
            effective_end = min(end_date or maximum_labeled_date, maximum_labeled_date)
            cursor.execute(
                """
                SELECT MAX(nav_date) AS snapshot_date
                FROM fund_nav_history
                WHERE nav_date BETWEEN %s AND %s
                GROUP BY LEFT(nav_date, 6)
                ORDER BY snapshot_date
                """,
                (start_date, effective_end),
            )
            dates = [str(row["snapshot_date"]) for row in cursor.fetchall()]
    finally:
        connection.close()
    selected_dates = dates[::step_months]
    saved = 0
    for snapshot_date in selected_dates:
        saved += build_historical_factor_snapshot(snapshot_date, config)
    return len(selected_dates), saved


def label_matured_snapshots(db_config: DatabaseConfig | None = None) -> int:
    config = db_config or database_config_from_env()
    ensure_schema(config)
    connection = connect(config)
    try:
        with connection.cursor() as cursor:
            cursor.execute("SELECT MAX(nav_date) AS max_date FROM fund_nav_history")
            value = cursor.fetchone()["max_date"]
            if not value:
                return 0
            mature_date = compact_date(parse_compact_date(str(value)) - timedelta(days=365))
            cursor.execute(
                """
                SELECT DISTINCT as_of_date
                FROM fund_score_factor_snapshot
                WHERE profitable IS NULL AND as_of_date <= %s
                ORDER BY as_of_date
                """,
                (mature_date,),
            )
            dates = [str(row["as_of_date"]) for row in cursor.fetchall()]
    finally:
        connection.close()
    updated = 0
    for as_of_text in dates:
        returns = _load_forward_returns_for_config(config, parse_compact_date(as_of_text))
        if not returns:
            continue
        connection = connect(config)
        try:
            with connection.cursor() as cursor:
                updated += cursor.executemany(
                    """
                    UPDATE fund_score_factor_snapshot
                    SET forward_return = %s, profitable = %s, updated_at = CURRENT_TIMESTAMP
                    WHERE as_of_date = %s AND fund_code = %s AND profitable IS NULL
                    """,
                    [
                        (forward_return, int(forward_return > 0), as_of_text, fund_code)
                        for fund_code, forward_return in returns.items()
                    ],
                )
            connection.commit()
        finally:
            connection.close()
    return updated


def _load_forward_returns_for_config(config: DatabaseConfig, as_of: date) -> dict[str, float]:
    connection = connect(config)
    try:
        return _load_forward_returns(connection, as_of)
    finally:
        connection.close()


def build_current_factor_snapshot(db_config: DatabaseConfig | None = None) -> tuple[str, int]:
    config = db_config or database_config_from_env()
    ensure_schema(config)
    connection = connect(config)
    try:
        with connection.cursor() as cursor:
            cursor.execute("SELECT MAX(nav_date) AS as_of_date FROM fund_nav_history")
            value = cursor.fetchone()["as_of_date"]
            if not value:
                return "", 0
            as_of_text = str(value)
            cursor.execute(
                """
                SELECT fund_code, fund_type, net_asset_scale
                FROM fund_detail
                WHERE fund_type IS NOT NULL AND fund_type <> ''
                """
            )
            profiles = {str(row["fund_code"]): row for row in cursor.fetchall()}

        as_of = parse_compact_date(as_of_text)
        nav_factors = _load_nav_factors(connection, as_of)
        performances = _latest_rows(connection, "fund_performance_history", "nav_date")
        ratings = _latest_rows(connection, "fund_rating", "rating_date")
        features = _latest_features(connection)

        rows: list[dict[str, Any]] = []
        for fund_code, profile in profiles.items():
            factors = dict(nav_factors.get(fund_code, {}))
            performance = performances.get(fund_code, {})
            fallbacks = {
                "return_1m": "monthly_return_rate",
                "return_3m": "three_month_return_rate",
                "return_6m": "six_month_return_rate",
                "return_1y": "one_year_return_rate",
                "return_2y": "two_year_return_rate",
                "return_3y": "three_year_return_rate",
            }
            for key, column in fallbacks.items():
                if factors.get(key) is None:
                    factors[key] = parse_number(performance.get(column))
            feature_rows = features.get(fund_code, {})
            for label, suffix in (("近1年", "1y"), ("近3年", "3y")):
                feature = feature_rows.get(label, {})
                if factors.get(f"volatility_{suffix}") is None:
                    factors[f"volatility_{suffix}"] = parse_number(feature.get("standard_deviation"))
                if factors.get(f"sharpe_{suffix}") is None:
                    factors[f"sharpe_{suffix}"] = parse_number(feature.get("sharpe_ratio"))
            rating = ratings.get(fund_code, {})
            factors.update(
                {
                    "rating_zhaoshang": parse_number(rating.get("zhaoshang_rating")),
                    "rating_shanghai_3y": parse_number(rating.get("shanghai_rating_3y")),
                    "rating_shanghai_5y": parse_number(rating.get("shanghai_rating_5y")),
                    "rating_jian": parse_number(rating.get("jian_rating")),
                    "rating_morningstar": parse_number(rating.get("morning_star_rating")),
                    "scale": parse_scale_yi(profile.get("net_asset_scale")),
                }
            )
            rows.append(
                {
                    "fund_code": fund_code,
                    "fund_type": str(profile["fund_type"]),
                    "comparison_group": str(profile["fund_type"]),
                    "factors": factors,
                    "normalized": {},
                }
            )

        exact_groups: dict[str, list[dict[str, Any]]] = defaultdict(list)
        for row in rows:
            exact_groups[row["fund_type"]].append(row)
        comparison_groups: dict[str, list[dict[str, Any]]] = defaultdict(list)
        for row in rows:
            group = row["fund_type"]
            if len(exact_groups[group]) < MIN_CATEGORY_SIZE:
                group = parent_group(group)
            row["comparison_group"] = group
            comparison_groups[group].append(row)

        for group_rows in comparison_groups.values():
            for factor in DEFAULT_WEIGHTS:
                if factor.startswith("rating_"):
                    for row in group_rows:
                        star = row["factors"].get(factor)
                        if star is not None:
                            row["normalized"][factor] = max(0.0, min(100.0, (star - 1.0) * 25.0))
                    continue
                scores = percentile_scores(group_rows, factor)
                for row in group_rows:
                    if row["fund_code"] in scores:
                        row["normalized"][factor] = scores[row["fund_code"]]

        snapshot_sql = """
            INSERT INTO fund_score_factor_snapshot (
              as_of_date, fund_code, fund_type, comparison_group,
              factors_json, normalized_json, data_coverage
            )
            VALUES (%s, %s, %s, %s, %s, %s, %s)
            ON DUPLICATE KEY UPDATE
              fund_type = VALUES(fund_type),
              comparison_group = VALUES(comparison_group),
              factors_json = VALUES(factors_json),
              normalized_json = VALUES(normalized_json),
              data_coverage = VALUES(data_coverage),
              updated_at = CURRENT_TIMESTAMP
        """
        values = []
        for row in rows:
            available = sum(
                DEFAULT_WEIGHTS[key] for key in DEFAULT_WEIGHTS if row["normalized"].get(key) is not None
            )
            values.append(
                (
                    as_of_text,
                    row["fund_code"],
                    row["fund_type"],
                    row["comparison_group"],
                    json.dumps(row["factors"], ensure_ascii=False),
                    json.dumps(row["normalized"], ensure_ascii=False),
                    available / 100.0,
                )
            )
        with connection.cursor() as cursor:
            cursor.executemany(snapshot_sql, values)
        connection.commit()
        return as_of_text, len(values)
    except Exception:
        connection.rollback()
        raise
    finally:
        connection.close()


def _load_profiles(connection, profile_id: int | None = None) -> list[dict[str, Any]]:
    sql = "SELECT * FROM fund_score_profile"
    params: tuple[Any, ...] = ()
    if profile_id is not None:
        sql += " WHERE id = %s"
        params = (profile_id,)
    else:
        sql += " WHERE is_active = 1"
    with connection.cursor() as cursor:
        cursor.execute(sql, params)
        return list(cursor.fetchall())


def _calibrated_probability(calibration: Any, score: float) -> float | None:
    if not calibration:
        return None
    if isinstance(calibration, str):
        try:
            calibration = json.loads(calibration)
        except json.JSONDecodeError:
            return None
    bins = calibration.get("bins") if isinstance(calibration, dict) else None
    if not bins:
        return None
    selected = min(bins, key=lambda item: abs(float(item["score"]) - score))
    return max(0.0, min(1.0, float(selected["probability"])))


def calculate_current_scores(
    profile_id: int | None = None,
    db_config: DatabaseConfig | None = None,
) -> tuple[str, int]:
    config = db_config or database_config_from_env()
    as_of_date, _ = build_current_factor_snapshot(config)
    if not as_of_date:
        return "", 0
    connection = connect(config)
    try:
        profiles = _load_profiles(connection, profile_id)
        with connection.cursor() as cursor:
            cursor.execute(
                """
                SELECT fund_code, comparison_group, factors_json, normalized_json
                FROM fund_score_factor_snapshot
                WHERE as_of_date = %s
                """,
                (as_of_date,),
            )
            snapshots = list(cursor.fetchall())
        total_saved = 0
        for profile in profiles:
            weights = validate_weights(json.loads(profile["weights_json"]))
            results: list[dict[str, Any]] = []
            for snapshot in snapshots:
                raw = json.loads(snapshot["factors_json"])
                normalized = json.loads(snapshot["normalized_json"] or "{}")
                total_score, coverage, components = score_factors(normalized, raw, weights)
                probability = None
                if profile["validation_status"] == "PASSED" and total_score is not None:
                    probability = _calibrated_probability(profile.get("calibration_json"), total_score)
                confidence = "INSUFFICIENT"
                if total_score is not None:
                    confidence = "HIGH" if coverage >= 0.90 and probability is not None else "MEDIUM"
                    if coverage < 0.80 or probability is None:
                        confidence = "LOW"
                results.append(
                    {
                        "fund_code": str(snapshot["fund_code"]),
                        "group": snapshot["comparison_group"],
                        "score": total_score,
                        "probability": probability,
                        "coverage": coverage,
                        "confidence": confidence,
                        "components": components,
                    }
                )
            grouped: dict[str, list[dict[str, Any]]] = defaultdict(list)
            for result in results:
                if result["score"] is not None:
                    grouped[str(result["group"])].append(result)
            for group_rows in grouped.values():
                group_rows.sort(key=lambda item: (-item["score"], item["fund_code"]))
                for rank, result in enumerate(group_rows, start=1):
                    result["rank"] = rank
                    result["count"] = len(group_rows)
            values = [
                (
                    profile["id"],
                    result["fund_code"],
                    as_of_date,
                    result["score"],
                    result["probability"],
                    result["confidence"],
                    result["coverage"],
                    result["group"],
                    result.get("rank"),
                    result.get("count"),
                    json.dumps(result["components"], ensure_ascii=False),
                    METHODOLOGY_VERSION,
                )
                for result in results
            ]
            with connection.cursor() as cursor:
                cursor.executemany(
                    """
                    INSERT INTO fund_score_result (
                      profile_id, fund_code, as_of_date, total_score,
                      profit_probability, confidence, data_coverage,
                      comparison_group, category_rank, category_count,
                      components_json, methodology_version
                    )
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                    ON DUPLICATE KEY UPDATE
                      total_score = VALUES(total_score),
                      profit_probability = VALUES(profit_probability),
                      confidence = VALUES(confidence),
                      data_coverage = VALUES(data_coverage),
                      comparison_group = VALUES(comparison_group),
                      category_rank = VALUES(category_rank),
                      category_count = VALUES(category_count),
                      components_json = VALUES(components_json),
                      methodology_version = VALUES(methodology_version),
                      updated_at = CURRENT_TIMESTAMP
                    """,
                    values,
                )
            total_saved += len(values)
        connection.commit()
        return as_of_date, total_saved
    except Exception:
        connection.rollback()
        raise
    finally:
        connection.close()


def _auc(pairs: list[tuple[float, int]]) -> float | None:
    positives = sum(label for _, label in pairs)
    negatives = len(pairs) - positives
    if positives == 0 or negatives == 0:
        return None
    ordered = sorted(pairs, key=lambda item: item[0])
    rank_sum = 0.0
    index = 0
    while index < len(ordered):
        end = index + 1
        while end < len(ordered) and ordered[end][0] == ordered[index][0]:
            end += 1
        average_rank = (index + 1 + end) / 2.0
        rank_sum += average_rank * sum(label for _, label in ordered[index:end])
        index = end
    return (rank_sum - positives * (positives + 1) / 2.0) / (positives * negatives)


def _calibration(train: list[tuple[float, int]]) -> dict[str, Any]:
    ordered = sorted(train)
    bins: list[dict[str, float]] = []
    if not ordered:
        return {"bins": []}
    bin_size = max(1, math.ceil(len(ordered) / 10))
    for start in range(0, len(ordered), bin_size):
        values = ordered[start : start + bin_size]
        bins.append(
            {
                "score": round(statistics.mean(score for score, _ in values), 4),
                "probability": round(statistics.mean(label for _, label in values), 6),
            }
        )
    for index in range(1, len(bins)):
        bins[index]["probability"] = max(bins[index - 1]["probability"], bins[index]["probability"])
    return {"bins": bins}


def _walk_forward_predictions(
    scored: list[tuple[str, float, int]],
) -> tuple[list[tuple[str, float, int, float, float]], list[dict[str, str]]]:
    unique_dates = sorted({value[0] for value in scored})
    if len(unique_dates) < 8:
        return [], []
    raw_boundaries = [
        min(len(unique_dates) - 1, int(len(unique_dates) * fraction))
        for fraction in (0.7, 0.8, 0.9)
    ]
    boundaries = sorted(set(raw_boundaries))
    predictions: list[tuple[str, float, int, float, float]] = []
    folds: list[dict[str, str]] = []
    for index, start_index in enumerate(boundaries):
        end_index = boundaries[index + 1] if index + 1 < len(boundaries) else len(unique_dates)
        test_dates = set(unique_dates[start_index:end_index])
        if not test_dates:
            continue
        test_start = unique_dates[start_index]
        cutoff = compact_date(parse_compact_date(test_start) - timedelta(days=365))
        train = [value for value in scored if value[0] <= cutoff]
        test = [value for value in scored if value[0] in test_dates]
        if len(train) < 100 or not test:
            continue
        calibration = _calibration([(score, label) for _, score, label in train])
        baseline = statistics.mean(label for _, _, label in train)
        fold_predictions = []
        for as_of_date, score, label in test:
            probability = _calibrated_probability(calibration, score)
            if probability is not None:
                fold_predictions.append((as_of_date, score, label, probability, baseline))
        if not fold_predictions:
            continue
        predictions.extend(fold_predictions)
        folds.append(
            {
                "trainStart": train[0][0],
                "trainEnd": train[-1][0],
                "testStart": min(test_dates),
                "testEnd": max(test_dates),
            }
        )
    return predictions, folds


def backtest_profile(profile_id: int, db_config: DatabaseConfig | None = None) -> dict[str, Any]:
    config = db_config or database_config_from_env()
    ensure_schema(config)
    connection = connect(config)
    try:
        profiles = _load_profiles(connection, profile_id)
        if not profiles:
            raise ValueError("score profile not found")
        profile = profiles[0]
        weights = validate_weights(json.loads(profile["weights_json"]))
        with connection.cursor() as cursor:
            cursor.execute(
                """
                SELECT as_of_date, factors_json, normalized_json, profitable
                FROM fund_score_factor_snapshot
                WHERE profitable IS NOT NULL
                ORDER BY as_of_date, fund_code
                """
            )
            rows = list(cursor.fetchall())
        scored: list[tuple[str, float, int]] = []
        for row in rows:
            raw = json.loads(row["factors_json"])
            normalized = json.loads(row["normalized_json"] or "{}")
            score, coverage, _ = score_factors(normalized, raw, weights)
            if score is not None and coverage >= MIN_SCORE_COVERAGE:
                scored.append((str(row["as_of_date"]), score, int(row["profitable"])))
        predictions, folds = _walk_forward_predictions(scored)
        if len(folds) < 3 or len(predictions) < 100:
            metrics = {
                "sampleCount": len(predictions),
                "availableSampleCount": len(scored),
                "foldCount": len(folds),
                "passed": False,
                "message": "历史标签不足，至少需要3个含12个月隔离期的时序回测折和100条样本",
            }
            calibration = {"bins": []}
        else:
            calibration = _calibration([(score, label) for _, score, label in scored])
            pairs = [(score, label) for _, score, label, _, _ in predictions]
            auc = _auc(pairs)
            brier = statistics.mean(
                (probability - label) ** 2
                for _, _, label, probability, _ in predictions
            )
            baseline_brier = statistics.mean(
                (baseline - label) ** 2
                for _, _, label, _, baseline in predictions
            )
            cutoff = sorted(score for _, score, _, _, _ in predictions)[
                max(0, int(len(predictions) * 0.8) - 1)
            ]
            top = [
                label
                for _, score, label, _, _ in predictions
                if score >= cutoff
            ]
            top_rate = statistics.mean(top) if top else None
            test_rate = statistics.mean(label for _, _, label, _, _ in predictions)
            lift = None if top_rate is None or test_rate is None else top_rate - test_rate
            fold_count = len(folds)
            passed = bool(
                len(predictions) >= 10000
                and fold_count >= 3
                and auc is not None
                and auc >= 0.52
                and brier is not None
                and baseline_brier is not None
                and brier < baseline_brier
                and lift is not None
                and lift >= 0.03
            )
            metrics = {
                "sampleCount": len(predictions),
                "availableSampleCount": len(scored),
                "foldCount": fold_count,
                "auc": auc,
                "brierScore": brier,
                "baselineBrierScore": baseline_brier,
                "top20WinRate": top_rate,
                "baselineWinRate": test_rate,
                "winRateLift": lift,
                "passed": passed,
                "folds": folds,
            }
        train_start = folds[0]["trainStart"] if folds else None
        train_end = folds[-1]["trainEnd"] if folds else None
        test_start = folds[0]["testStart"] if folds else None
        test_end = folds[-1]["testEnd"] if folds else None
        with connection.cursor() as cursor:
            cursor.execute(
                """
                INSERT INTO fund_score_backtest (
                  profile_id, train_start_date, train_end_date,
                  test_start_date, test_end_date, sample_count, fold_count,
                  auc, brier_score, baseline_brier_score, top20_win_rate,
                  baseline_win_rate, win_rate_lift, passed,
                  limitations_json, metrics_json
                )
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                """,
                (
                    profile_id,
                    train_start,
                    train_end,
                    test_start,
                    test_end,
                    metrics.get("sampleCount", 0),
                    metrics.get("foldCount", 0),
                    metrics.get("auc"),
                    metrics.get("brierScore"),
                    metrics.get("baselineBrierScore"),
                    metrics.get("top20WinRate"),
                    metrics.get("baselineWinRate"),
                    metrics.get("winRateLift"),
                    int(bool(metrics.get("passed"))),
                    json.dumps(
                        {
                            "survivorshipBias": True,
                            "notes": [
                                "历史基金清盘样本可能不完整",
                                "历史细分类型缺失时使用当前基金类型作为比较组代理",
                                "首版无风险利率固定为0",
                                "采用滚动时序验证，训练集与测试集之间隔离12个月",
                            ],
                        },
                        ensure_ascii=False,
                    ),
                    json.dumps(metrics, ensure_ascii=False),
                ),
            )
            cursor.execute(
                """
                UPDATE fund_score_profile
                SET calibration_json = %s,
                    validation_status = %s,
                    status = CASE WHEN status = 'PENDING_BACKTEST' THEN 'CANDIDATE' ELSE status END
                WHERE id = %s
                """,
                (
                    json.dumps(calibration, ensure_ascii=False),
                    "PASSED" if metrics.get("passed") else "FAILED",
                    profile_id,
                ),
            )
        connection.commit()
        calculate_current_scores(profile_id, config)
        return metrics
    except Exception:
        connection.rollback()
        raise
    finally:
        connection.close()


def _random_recommended_weights(randomizer: random.Random) -> dict[str, int]:
    block_values: dict[str, int] = {}
    remaining = 100
    names = list(BLOCKS)
    for name in names[:-1]:
        minimum, maximum = BLOCKS[name][1]
        value = randomizer.randint(minimum, maximum)
        block_values[name] = value
        remaining -= value
    last = names[-1]
    minimum, maximum = BLOCKS[last][1]
    if remaining < minimum or remaining > maximum:
        return _random_recommended_weights(randomizer)
    block_values[last] = remaining
    weights: dict[str, int] = {}
    for name, (keys, _) in BLOCKS.items():
        block_total = block_values[name]
        seed_total = sum(DEFAULT_WEIGHTS[key] for key in keys)
        allocated = 0
        for key in keys[:-1]:
            value = round(block_total * DEFAULT_WEIGHTS[key] / seed_total) if seed_total else 0
            weights[key] = value
            allocated += value
        weights[keys[-1]] = block_total - allocated
    return validate_weights(weights)


def recommend_weights(db_config: DatabaseConfig | None = None, candidates: int = 300) -> int:
    config = db_config or database_config_from_env()
    ensure_schema(config)
    connection = connect(config)
    try:
        with connection.cursor() as cursor:
            cursor.execute(
                """
                SELECT as_of_date, normalized_json, factors_json, profitable
                FROM fund_score_factor_snapshot
                WHERE profitable IS NOT NULL
                ORDER BY as_of_date, fund_code
                """
            )
            snapshots = list(cursor.fetchall())
        unique_dates = sorted({str(snapshot["as_of_date"]) for snapshot in snapshots})
        if len(unique_dates) < 8 or len(snapshots) < 100:
            raise ValueError("历史标签不足，无法生成推荐权重")
        first_test_date = unique_dates[min(len(unique_dates) - 1, int(len(unique_dates) * 0.7))]
        training_cutoff = compact_date(parse_compact_date(first_test_date) - timedelta(days=365))
        training_snapshots = [
            snapshot for snapshot in snapshots
            if str(snapshot["as_of_date"]) <= training_cutoff
        ]
        if len(training_snapshots) < 100:
            raise ValueError("隔离未来12个月后历史训练样本不足，无法生成推荐权重")
        randomizer = random.Random(20260731)
        best_weights = DEFAULT_WEIGHTS
        best_rate = -1.0
        for _ in range(candidates):
            weights = _random_recommended_weights(randomizer)
            scored: list[tuple[float, int]] = []
            for snapshot in training_snapshots:
                score, coverage, _ = score_factors(
                    json.loads(snapshot["normalized_json"] or "{}"),
                    json.loads(snapshot["factors_json"]),
                    weights,
                )
                if score is not None and coverage >= MIN_SCORE_COVERAGE:
                    scored.append((score, int(snapshot["profitable"])))
            if len(scored) < 100:
                continue
            scored.sort(reverse=True)
            top = scored[: max(1, len(scored) // 5)]
            rate = statistics.mean(label for _, label in top)
            if rate > best_rate:
                best_rate = rate
                best_weights = weights
        with connection.cursor() as cursor:
            cursor.execute(
                "SELECT COALESCE(MAX(version_no), 0) + 1 AS version_no FROM fund_score_profile WHERE profile_name = '未来盈利推荐'"
            )
            version = int(cursor.fetchone()["version_no"])
            cursor.execute(
                """
                INSERT INTO fund_score_profile (
                  profile_name, version_no, status, source_type, target_months,
                  weights_json, validation_status, is_active, created_by
                )
                VALUES ('未来盈利推荐', %s, 'PENDING_BACKTEST', 'BACKTEST', 12, %s, 'UNVERIFIED', 0, 'system')
                """,
                (version, json.dumps(best_weights, ensure_ascii=False)),
            )
            profile_id = int(cursor.lastrowid)
        connection.commit()
    finally:
        connection.close()
    backtest_profile(profile_id, config)
    return profile_id


def process_pending_jobs(db_config: DatabaseConfig | None = None, limit: int = 10) -> int:
    config = db_config or database_config_from_env()
    ensure_schema(config)
    processed = 0
    while processed < limit:
        connection = connect(config)
        try:
            with connection.cursor() as cursor:
                cursor.execute(
                    """
                    SELECT * FROM fund_score_job
                    WHERE status = 'PENDING'
                    ORDER BY created_at, id
                    LIMIT 1
                    FOR UPDATE
                    """
                )
                job = cursor.fetchone()
                if not job:
                    connection.rollback()
                    return processed
                cursor.execute(
                    "UPDATE fund_score_job SET status = 'RUNNING', started_at = NOW() WHERE id = %s",
                    (job["id"],),
                )
            connection.commit()
        finally:
            connection.close()
        try:
            if job["job_type"] == "CURRENT_SCORE":
                _, count = calculate_current_scores(job.get("profile_id"), config)
                message = f"已生成{count}条评分"
            elif job["job_type"] == "BACKTEST":
                metrics = backtest_profile(int(job["profile_id"]), config)
                message = json.dumps(metrics, ensure_ascii=False)
            elif job["job_type"] == "RECOMMEND":
                profile_id = recommend_weights(config)
                message = f"已生成推荐方案{profile_id}"
            else:
                raise ValueError(f"unsupported score job type: {job['job_type']}")
            status = "SUCCESS"
        except Exception as exc:
            status = "FAILED"
            message = str(exc)[:1000]
        connection = connect(config)
        try:
            with connection.cursor() as cursor:
                cursor.execute(
                    """
                    UPDATE fund_score_job
                    SET status = %s, message = %s, finished_at = NOW()
                    WHERE id = %s
                    """,
                    (status, message, job["id"]),
                )
            connection.commit()
        finally:
            connection.close()
        processed += 1
    return processed
