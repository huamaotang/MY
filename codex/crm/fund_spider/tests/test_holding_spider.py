from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from spiders.holding_spider import parse_holding_rows  # noqa: E402


def holding_response(title: str) -> str:
    content = f"""
      <h4>{title}</h4>
      <table>
        <tr>
          <th>序号</th><th>股票代码</th><th>股票名称</th><th>最新价</th><th>涨跌幅</th>
          <th>相关资讯</th><th>占净值比例</th><th>持股数（万股）</th><th>持仓市值（万元）</th>
        </tr>
        <tr>
          <td>1</td><td>002821</td><td>凯莱英</td><td>162.95</td><td>2.99%</td>
          <td><a href="//quote.eastmoney.com/unify/r/0.002821">行情</a></td>
          <td>10.57%</td><td>1,616.15</td><td>261,816.49</td>
        </tr>
      </table>
    """
    return f"var apidata={{content:{json.dumps(content, ensure_ascii=False)}}};"


class HoldingParserTest(unittest.TestCase):
    def test_parses_explicit_page_cutoff_date(self) -> None:
        rows = parse_holding_rows(
            "003095",
            holding_response(
                "中欧医疗健康混合A 2026年2季度股票投资明细 "
                "来源：天天基金 截止至：2026-06-30"
            ),
        )

        self.assertEqual(1, len(rows))
        self.assertEqual("20260630", rows[0].report_date)
        self.assertEqual("20260630", rows[0].cutoff_date)
        self.assertEqual("10.57", rows[0].net_value_ratio)

    def test_falls_back_to_report_date_when_cutoff_is_missing(self) -> None:
        rows = parse_holding_rows(
            "003095",
            holding_response("中欧医疗健康混合A 2026年2季度股票投资明细"),
        )

        self.assertEqual("20260630", rows[0].report_date)
        self.assertEqual("20260630", rows[0].cutoff_date)

    def test_keeps_report_period_and_page_cutoff_as_separate_fields(self) -> None:
        rows = parse_holding_rows(
            "003095",
            holding_response(
                "中欧医疗健康混合A 2026年2季度股票投资明细 截止至：2026-06-29"
            ),
        )

        self.assertEqual("20260630", rows[0].report_date)
        self.assertEqual("20260629", rows[0].cutoff_date)


if __name__ == "__main__":
    unittest.main()
