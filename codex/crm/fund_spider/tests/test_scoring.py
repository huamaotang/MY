from __future__ import annotations

import sys
import unittest
from datetime import date, timedelta
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from scoring import (  # noqa: E402
    DEFAULT_WEIGHTS,
    BOND_WEIGHTS,
    _profile_matches_fund_type,
    NavPoint,
    _walk_forward_predictions,
    calculate_nav_factors,
    parse_scale_yi,
    percentile_scores,
    score_factors,
    validate_weights,
)


class ScoringCalculatorTest(unittest.TestCase):
    def test_calculates_returns_risk_and_drawdown_without_future_points(self) -> None:
        start = date(2022, 1, 1)
        points = [
            NavPoint(
                start + timedelta(days=index),
                1 + index / 1000,
                0.001 + (0.0001 if index % 2 == 0 else -0.0001),
            )
            for index in range(1120)
        ]
        factors = calculate_nav_factors(points)

        self.assertIsNotNone(factors["return_1y"])
        self.assertIsNotNone(factors["return_3y"])
        self.assertGreater(factors["sharpe_1y"], 0)
        self.assertEqual(0, factors["drawdown_1y"])
        self.assertIsNotNone(factors["decline_1d"])
        self.assertIsNotNone(factors["decline_1w"])
        self.assertIsNotNone(factors["decline_2w"])
        self.assertIsNotNone(factors["decline_3w"])
        self.assertIsNotNone(factors["decline_4w"])

        earlier = calculate_nav_factors(points, 800)
        self.assertIsNone(earlier["return_3y"])
        self.assertIsNotNone(earlier["decline_1d"])

    def test_calculates_short_term_decline_windows(self) -> None:
        start = date(2023, 1, 1)
        points = [
            NavPoint(start + timedelta(days=index), 1.0 + index / 100, None)
            for index in range(35)
        ]
        factors = calculate_nav_factors(points)

        self.assertAlmostEqual((1.34 / 1.33 - 1.0) * 100.0, factors["decline_1d"], places=6)
        self.assertAlmostEqual((1.34 / 1.27 - 1.0) * 100.0, factors["decline_1w"], places=6)
        self.assertAlmostEqual((1.34 / 1.20 - 1.0) * 100.0, factors["decline_2w"], places=6)
        self.assertAlmostEqual((1.34 / 1.13 - 1.0) * 100.0, factors["decline_3w"], places=6)
        self.assertAlmostEqual((1.34 / 1.06 - 1.0) * 100.0, factors["decline_4w"], places=6)

        self.assertIsNone(calculate_nav_factors([NavPoint(start, 1.0, None)])["decline_1d"])

    def test_bigger_decline_scores_higher_percentile(self) -> None:
        rows = [
            {"fund_code": "A", "factors": {"decline_4w": -12.0}},
            {"fund_code": "B", "factors": {"decline_4w": -3.0}},
            {"fund_code": "C", "factors": {"decline_4w": 1.5}},
        ]
        scores = percentile_scores(rows, "decline_4w")

        self.assertEqual(100.0, scores["A"])
        self.assertEqual(50.0, scores["B"])
        self.assertEqual(0.0, scores["C"])
        self.assertGreater(scores["A"], scores["B"])

    def test_bigger_today_decline_scores_higher_percentile(self) -> None:
        rows = [
            {"fund_code": "A", "factors": {"decline_today": -4.2}},
            {"fund_code": "B", "factors": {"decline_today": 0.3}},
        ]
        scores = percentile_scores(rows, "decline_today")

        self.assertEqual(100.0, scores["A"])
        self.assertEqual(0.0, scores["B"])
        self.assertGreater(scores["A"], scores["B"])

    def test_score_requires_one_year_return_and_seventy_percent_coverage(self) -> None:
        normalized = {key: 80.0 for key in DEFAULT_WEIGHTS}
        raw = {key: 1.0 for key in DEFAULT_WEIGHTS}
        score, coverage, components = score_factors(normalized, raw, DEFAULT_WEIGHTS)
        self.assertEqual(80.0, score)
        self.assertEqual(1.0, coverage)
        self.assertEqual(len(DEFAULT_WEIGHTS), len(components))

        raw["return_1y"] = None
        normalized["return_1y"] = None
        score, _, _ = score_factors(normalized, raw, DEFAULT_WEIGHTS)
        self.assertIsNone(score)

    def test_weight_validation_and_scale_parsing(self) -> None:
        self.assertEqual(DEFAULT_WEIGHTS, validate_weights(DEFAULT_WEIGHTS))
        invalid = dict(DEFAULT_WEIGHTS)
        invalid["scale"] += 1
        with self.assertRaisesRegex(ValueError, "total 100"):
            validate_weights(invalid)
        self.assertEqual(12.5, parse_scale_yi("12.50亿元"))
        self.assertEqual(1.0, parse_scale_yi("10000万元"))

    def test_walk_forward_backtest_uses_three_embargoed_folds(self) -> None:
        scored = []
        current = date(2015, 1, 31)
        for month in range(96):
            as_of = current + timedelta(days=month * 30)
            for sample in range(10):
                label = sample % 2
                scored.append((as_of.strftime("%Y%m%d"), 70.0 if label else 30.0, label))

        predictions, folds = _walk_forward_predictions(scored)

        self.assertEqual(3, len(folds))
        self.assertGreater(len(predictions), 100)
        for fold in folds:
            train_end = date.fromisoformat(
                f"{fold['trainEnd'][:4]}-{fold['trainEnd'][4:6]}-{fold['trainEnd'][6:]}"
            )
            test_start = date.fromisoformat(
                f"{fold['testStart'][:4]}-{fold['testStart'][4:6]}-{fold['testStart'][6:]}"
            )
            self.assertGreaterEqual((test_start - train_end).days, 365)

    def test_bond_weights_have_decline_today_zero(self) -> None:
        self.assertEqual(0, BOND_WEIGHTS["decline_today"])
        self.assertEqual(100, sum(BOND_WEIGHTS.values()))
        self.assertEqual(len(DEFAULT_WEIGHTS), len(BOND_WEIGHTS))

    def test_bond_weights_total_100(self) -> None:
        self.assertEqual(100, sum(BOND_WEIGHTS.values()))

    def test_bond_weights_exclude_decline_today_effect(self) -> None:
        normalized = {key: 80.0 for key in BOND_WEIGHTS}
        raw = {key: 1.0 for key in BOND_WEIGHTS}
        score_bond, coverage_bond, components_bond = score_factors(normalized, raw, BOND_WEIGHTS)
        self.assertIsNotNone(score_bond)
        self.assertEqual(1.0, coverage_bond)

        normalized_stock = {key: 80.0 for key in DEFAULT_WEIGHTS}
        raw_stock = {key: 1.0 for key in DEFAULT_WEIGHTS}
        score_stock, coverage_stock, _ = score_factors(normalized_stock, raw_stock, DEFAULT_WEIGHTS)
        self.assertIsNotNone(score_stock)
        self.assertAlmostEqual(score_bond, score_stock, places=2)

    def test_profile_matches_fund_type(self) -> None:
        stock_profile = {"profile_name": "股票类配置", "fund_type_scope": "*"}
        bond_profile = {"profile_name": "债类配置", "fund_type_scope": "债券型,货币型"}
        self.assertTrue(_profile_matches_fund_type(stock_profile, "股票型"))
        self.assertTrue(_profile_matches_fund_type(stock_profile, "债券型"))
        self.assertTrue(_profile_matches_fund_type(bond_profile, "债券型"))
        self.assertTrue(_profile_matches_fund_type(bond_profile, "货币型"))
        self.assertFalse(_profile_matches_fund_type(bond_profile, "股票型"))
        self.assertFalse(_profile_matches_fund_type(bond_profile, "混合型"))

    def test_validate_weights_accepts_bond_weights(self) -> None:
        self.assertEqual(BOND_WEIGHTS, validate_weights(BOND_WEIGHTS))
        with self.assertRaises(ValueError):
            invalid = dict(BOND_WEIGHTS)
            del invalid["decline_today"]
            validate_weights(invalid)


if __name__ == "__main__":
    unittest.main()
