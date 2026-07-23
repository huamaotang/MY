from __future__ import annotations

import sys
import unittest
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from rating_spider import parse_rating_list


class RatingListParserTest(unittest.TestCase):
    def test_parses_current_institution_ratings_and_uses_latest_date(self) -> None:
        html = """
        <script>
        var JG_2_pjrq = "";
        var JG_3_pjrq = "";
        var JG_4_pjrq = "";
        var JG_5_pjrq = "";
        var fundinfos = "000001|基金甲|混合型|经理||公司||3|||5|1|4|0|3|-1|2|1_000002|基金乙|债券型|经理||公司|||||--||0||--||--|";
        var JG_2_pjrq = "2026-06-30";
        var JG_3_pjrq = "2024-09-30";
        var JG_4_pjrq = "2026-03-31";
        var JG_5_pjrq = "2024-06-30";
        </script>
        """

        rows = parse_rating_list(html)

        self.assertEqual(1, len(rows))
        row = rows[0]
        self.assertEqual("000001", row.fund_code)
        self.assertEqual("20260630", row.rating_date)
        self.assertEqual(5, row.zhaoshang_rating)
        self.assertEqual(4, row.shanghai_rating_3y)
        self.assertIsNone(row.shanghai_rating_5y)
        self.assertEqual(2, row.jian_rating)
        self.assertEqual(3, row.morning_star_rating)

    def test_requires_embedded_list_and_rating_date(self) -> None:
        with self.assertRaisesRegex(ValueError, "fundinfos"):
            parse_rating_list("<html></html>")

        with self.assertRaisesRegex(ValueError, "rating date"):
            parse_rating_list('var fundinfos = "000001|基金";')


if __name__ == "__main__":
    unittest.main()
