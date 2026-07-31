from __future__ import annotations

import sys
import unittest
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from spiders.profile_spider import parse_profile  # noqa: E402


class ProfileParserTest(unittest.TestCase):
    def test_fund_type_uses_text_before_hyphen(self) -> None:
        html = """
        <div class="bs_gl">
          <label>成立日期：2020-01-02</label>
          <label>基金经理：张三</label>
          <label>类型：债券型-混合一级</label>
          <label>管理人：示例基金公司</label>
        </div></div>
        """

        profile = parse_profile("000001", html)

        self.assertEqual("债券型", profile.fund_type)

    def test_fund_type_without_hyphen_is_unchanged(self) -> None:
        html = """
        <div class="bs_gl">
          <label>类型：股票型</label>
        </div></div>
        """

        profile = parse_profile("000002", html)

        self.assertEqual("股票型", profile.fund_type)


if __name__ == "__main__":
    unittest.main()
