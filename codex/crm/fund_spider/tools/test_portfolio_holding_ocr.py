import unittest

from fund_spider.tools.portfolio_holding_ocr import OcrBlock, parse_rows


def block(text, x, y, score=0.99):
    return OcrBlock(text=text, score=score, x0=x, y0=y, x1=x + 120, y1=y + 40)


class PortfolioHoldingOcrTest(unittest.TestCase):
    def test_alipay_holding_layout(self):
        blocks = [
            block("名称", 70, 620),
            block("金额/昨日收益", 650, 620),
            block("持有收益/率", 1010, 620),
            block("国富沪港深成长精选股票A", 70, 770),
            block("9,898.16", 700, 770),
            block("-121.37", 730, 840),
            block("+2,717.77", 1020, 770),
            block("+37.85%", 1070, 840),
        ]

        warnings = []
        rows = parse_rows(blocks, "2026-07-30", warnings, "alipay", "holding")

        self.assertEqual(1, len(rows))
        self.assertEqual("国富沪港深成长精选股票A", rows[0]["fundName"])
        self.assertEqual("9898.16", rows[0]["holdingAmount"])
        self.assertEqual("-121.37", rows[0]["yesterdayProfit"])
        self.assertEqual("2717.77", rows[0]["holdingProfit"])
        self.assertEqual("37.85", rows[0]["holdingReturnRate"])

    def test_alipay_holding_joins_wrapped_fund_name(self):
        blocks = [
            block("名称", 70, 620),
            block("金额/昨日收益", 650, 620),
            block("易方达中证海外中国互联", 70, 770),
            block("网50ETF联接A", 70, 815),
            block("8,000.00", 700, 770),
            block("+12.00", 730, 840),
        ]

        rows = parse_rows(blocks, "2026-07-30", [], "alipay", "holding")

        self.assertEqual(1, len(rows))
        self.assertEqual("易方达中证海外中国互联网50ETF联接A", rows[0]["fundName"])
        self.assertEqual("8000.00", rows[0]["holdingAmount"])

    def test_account_list_layout(self):
        blocks = [
            block("当日收益", 562, 620),
            block("关联板块", 817, 620),
            block("持有收益", 1069, 621),
            block("招商国证生物医药指...", 37, 767),
            block("-1.76%", 865, 764),
            block("-4530.35", 1077, 763),
            block("生物医药", 871, 834),
            block("-7.77%", 1161, 833),
            block("￥ 53,765.62", 43, 836),
        ]

        warnings = []
        rows = parse_rows(blocks, "2026-07-28", warnings, "tencent")

        self.assertEqual(1, len(rows))
        self.assertEqual("招商国证生物医药指", rows[0]["fundName"])
        self.assertEqual("53765.62", rows[0]["holdingAmount"])
        self.assertEqual("-4530.35", rows[0]["holdingProfit"])
        self.assertEqual("-7.77", rows[0]["holdingReturnRate"])
        self.assertEqual([], warnings)

    def test_tencent_holding_layout_filters_recommendations(self):
        blocks = [
            block("资产明细", 70, 390),
            block("招商国证生物医药指数A", 70, 570),
            block("持有金额", 70, 690),
            block("53,670.40", 70, 760),
            block("持仓收益", 500, 690),
            block("-4,629.60", 500, 760),
            block("昨日收益", 900, 690),
            block("+802.67", 900, 755),
            block("产品解读", 100, 850),
            block("鹏华弘利混合C", 380, 1500),
            block("+9.81%", 380, 1600),
            block("持仓服务", 550, 1150),
        ]

        warnings = []
        rows = parse_rows(blocks, "2026-07-30", warnings, "tencent", "holding")

        self.assertEqual(1, len(rows))
        self.assertEqual("招商国证生物医药指数A", rows[0]["fundName"])
        self.assertEqual("53670.40", rows[0]["holdingAmount"])
        self.assertEqual("-4629.60", rows[0]["holdingProfit"])
        self.assertEqual("802.67", rows[0]["yesterdayProfit"])

    def test_alipay_trade_layout_marks_closed_transaction(self):
        blocks = [
            block("交易记录", 850, 180),
            block("买入", 80, 490),
            block("基金1易方达北证50指数C", 220, 490),
            block("500.00元", 1000, 490),
            block("2026-07-20 14:42:55", 220, 560),
            block("买入", 80, 720),
            block("基金一易方达北证50指数C", 220, 720),
            block("3，445.08元", 1000, 720),
            block("2026-07-2014:42:39", 220, 790),
            block("交易关闭", 1030, 790),
        ]

        warnings = []
        rows = parse_rows(blocks, "2026-07-30", warnings, "alipay", "trade")

        self.assertEqual(2, len(rows))
        self.assertEqual("BUY", rows[0]["operationType"])
        self.assertEqual("易方达北证50指数C", rows[0]["fundName"])
        self.assertEqual("500.00", rows[0]["transactionAmount"])
        self.assertEqual("UNKNOWN", rows[0]["transactionStatus"])
        self.assertEqual("3445.08", rows[1]["transactionAmount"])
        self.assertEqual("2026-07-20T14:42:39", rows[1]["transactionAt"])
        self.assertEqual("FAILED", rows[1]["transactionStatus"])

    def test_tencent_trade_layout_reads_successful_buys(self):
        blocks = [
            block("交易明细", 70, 320),
            block("买入", 70, 680),
            block("招商国证生物医药指数A", 230, 680),
            block("+7,000.00元", 960, 680),
            block("银行卡买入", 230, 750),
            block("买入成功", 1040, 750),
            block("2026-06-08 14:13:35", 230, 820),
        ]

        warnings = []
        rows = parse_rows(blocks, "2026-07-30", warnings, "tencent", "trade")

        self.assertEqual(1, len(rows))
        self.assertEqual("招商国证生物医药指数A", rows[0]["fundName"])
        self.assertEqual("7000.00", rows[0]["transactionAmount"])
        self.assertEqual("2026-06-08T14:13:35", rows[0]["transactionAt"])
        self.assertEqual("SUCCESS", rows[0]["transactionStatus"])


if __name__ == "__main__":
    unittest.main()
