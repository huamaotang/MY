from __future__ import annotations

import sys
import unittest
from datetime import date, timedelta
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from scoring import (  # noqa: E402
    DEFAULT_WEIGHTS,
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


if __name__ == "__main__":
    unittest.main()
