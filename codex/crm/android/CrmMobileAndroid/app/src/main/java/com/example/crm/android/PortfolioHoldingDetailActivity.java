package com.example.crm.android;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PortfolioHoldingDetailActivity extends Activity {
    private static final int CHART_NAV_SIZE = 1000;
    private static final String[] PERIOD_KEYS = {"1M", "3M", "6M", "1Y", "3Y", "ALL"};
    private static final String[] PERIOD_LABELS = {"近1月", "近3月", "近6月", "近1年", "近3年", "成立以来"};

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<FundNav> chartNavs = new ArrayList<>();

    private SessionStore session;
    private UserFundHolding holding;
    private FundDetail detail;
    private Fund fund;
    private String trendPeriod = "1Y";
    private LinearLayout content;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionStore(this);
        if (!session.isAuthenticated()) {
            finish();
            return;
        }
        holding = (UserFundHolding) getIntent().getSerializableExtra("holding");
        if (holding == null || holding.fundCode == null || holding.fundCode.trim().isEmpty()) {
            finish();
            return;
        }
        fund = fallbackFund();
        buildContent();
        render();
        loadFundData();
    }

    private void buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 16), Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 12));
        setContentView(root);

        root.addView(Ui.text(this, "持仓详情", 24, Ui.TEXT, Typeface.BOLD));
        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
        root.addView(progressBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 42)));

        ScrollView scrollView = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(content);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
    }

    private void loadFundData() {
        progressBar.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            try {
                FundDetail result = session.apiClient().fundDetail(holding.fundCode);
                PageResult<FundNav> navResult = session.apiClient()
                        .listFundNavs(holding.fundCode, 1, CHART_NAV_SIZE);
                runOnUiThread(() -> {
                    detail = result;
                    chartNavs.clear();
                    chartNavs.addAll(navResult.records);
                    if (result.fund != null) {
                        fund = result.fund;
                    }
                    progressBar.setVisibility(View.GONE);
                    render();
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Ui.toast(this, ex.getMessage());
                });
            }
        });
    }

    private void render() {
        content.removeAllViews();
        content.addView(Ui.text(this, Ui.value(holding.fundName), 21, Ui.TEXT, Typeface.BOLD));
        content.addView(Ui.text(this, "代码：" + Ui.value(holding.fundCode), 14, Ui.BLUE, Typeface.BOLD));

        Button fundDetailButton = new Button(this);
        fundDetailButton.setText("查看基金详情");
        fundDetailButton.setAllCaps(false);
        fundDetailButton.setOnClickListener(view -> openFundDetail());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 46));
        buttonParams.setMargins(0, Ui.dp(this, 12), 0, 0);
        content.addView(fundDetailButton, buttonParams);

        section("持仓数据");
        row("持有金额", holding.holdingAmount);
        signedValueRow("持有收益", holding.holdingProfit);
        signedPercentRow("持有收益率", holding.holdingReturnRate);
        row("持有成本", holding.holdingCost);
        signedValueRow("昨日收益", holding.yesterdayProfit);
        signedValueRow("今日收益", holding.todayProfit);
        row("持有份额", holding.holdingShares);
        row("净值成本", holding.costNav);
        row("估值日期", PortfolioHoldingActivity.valuationDateTime(holding));
        row("重仓报告日", holding.holdingReportDate);
        signedPercentRow("当日预估涨跌", holding.estimatedChangeRate);
        signedValueRow("预估当日盈亏", holding.estimatedDailyProfit);
        row("估值后金额", holding.estimatedHoldingAmount);
        row("预估单位净值", holding.estimatedUnitNav);
        signedPercentRow("累计预估涨跌", holding.estimatedCumulativeChangeRate);
        signedValueRow("累计预估盈亏", holding.estimatedCumulativeProfit);
        percentRow("行情覆盖率", holding.valuationCoverageRate);
        row("行情更新时间", holding.valuationUpdatedAt);
        row("截图日期", holding.screenshotDate);
        row("导入批次", holding.latestImportId);
        row("导入时间", holding.latestImportAt);
        row("记录创建时间", holding.createdAt);
        row("记录更新时间", holding.updatedAt);

        section("净值与收益走势");
        renderPeriodButtons();
        List<FundTrendChartView.TrendRow> trendRows =
                ProductDetailActivity.buildTrendRows(chartNavs, trendPeriod);
        FundTrendChartView navChart = new FundTrendChartView(this);
        navChart.setData("净值走势图", trendRows, ProductDetailActivity.navSeries());
        content.addView(navChart, chartLayoutParams());
        FundTrendChartView returnChart = new FundTrendChartView(this);
        returnChart.setData("收益走势图", trendRows, ProductDetailActivity.returnSeries());
        content.addView(returnChart, chartLayoutParams());

        renderFundData();
    }

    private void renderFundData() {
        section("基金基础信息");
        row("基金名称", fund.fundName);
        row("基金代码", fund.fundCode);
        row("类型", fund.fundType);
        row("基金经理", fund.fundManager);
        row("管理人", fund.managementCompany);
        row("成立日期", fund.inceptionDate);
        row("净资产规模", fund.netAssetScale);
        row("规模截止", fund.scaleDate);
        row("购买状态", fund.canBuy ? "可购买" : "不可购买");
        row("基金创建时间", fund.createdAt);
        row("基金更新时间", fund.updatedAt);

        FundNav nav = detail == null ? null : detail.latestNav;
        section("最新净值");
        row("净值日期", nav == null ? null : nav.navDate);
        row("单位净值", nav == null ? null : nav.unitNav);
        row("累计净值", nav == null ? null : nav.accumulatedNav);
        signedPercentRow("日增长率", nav == null ? null : toDouble(nav.dailyGrowthRate));

        FundDailyValuation valuation = detail == null ? null : detail.latestValuation;
        section("每日估值");
        row("估值日期", valuation == null ? null
                : preciseValuationDate(valuation.quoteUpdatedAt, valuation.valuationDate));
        signedPercentRow("预估涨跌幅", valuation == null ? null : toDouble(valuation.estimatedChangeRate));
        row("预估单位净值", valuation == null ? null : valuation.estimatedUnitNav);
        row("基准净值日期", valuation == null ? null : valuation.baseNavDate);
        row("基准单位净值", valuation == null ? null : valuation.baseUnitNav);
        row("重仓报告日", valuation == null ? null : valuation.holdingReportDate);
        row("重仓占净值", valuation == null ? null : percent(valuation.holdingWeight));
        row("有行情占净值", valuation == null ? null : percent(valuation.quotedHoldingWeight));
        row("行情覆盖率", valuation == null ? null : percent(valuation.quoteCoverageRate));
        row("重仓数量", valuation == null ? null : valuation.holdingCount);
        row("有行情数量", valuation == null ? null : valuation.quotedHoldingCount);
        row("行情更新时间", valuation == null ? null : valuation.quoteUpdatedAt);

        FundPerformance performance = detail == null ? null : detail.latestPerformance;
        section("业绩表现");
        row("净值日期", performance == null ? null : performance.navDate);
        performanceRow("近一周", performance == null ? null : performance.weeklyReturnRate);
        performanceRow("近一月", performance == null ? null : performance.monthlyReturnRate);
        performanceRow("近三月", performance == null ? null : performance.threeMonthReturnRate);
        performanceRow("近六月", performance == null ? null : performance.sixMonthReturnRate);
        performanceRow("近一年", performance == null ? null : performance.oneYearReturnRate);
        performanceRow("近两年", performance == null ? null : performance.twoYearReturnRate);
        performanceRow("近三年", performance == null ? null : performance.threeYearReturnRate);
        performanceRow("今年以来", performance == null ? null : performance.yearToDateReturnRate);
        performanceRow("成立以来", performance == null ? null : performance.sinceInceptionReturnRate);
        row("自定义区间", performance == null ? null
                : Ui.value(performance.customStartDate) + " 至 " + Ui.value(performance.customEndDate));
        performanceRow("区间收益", performance == null ? null : performance.customReturnRate);
        performanceRow("原手续费", performance == null ? null : performance.originalFeeRate);
        performanceRow("折后手续费", performance == null ? null : performance.discountedFeeRate);
        performanceRow("活期宝手续费", performance == null ? null : performance.cashManagementFeeRate);

        section("最新重仓");
        if (detail == null || detail.latestHoldings.isEmpty()) {
            row("重仓", null);
        } else {
            for (FundHolding item : detail.latestHoldings) {
                String label = (item.rankNo == null ? "" : item.rankNo + ". ")
                        + Ui.value(item.stockName) + " " + Ui.value(item.stockCode);
                String value = "报告 " + Ui.value(item.reportPeriod)
                        + " / " + Ui.value(item.reportDate)
                        + " / 占净值 " + Ui.value(percent(item.netValueRatio))
                        + " / 最新价 " + Ui.value(item.latestPrice)
                        + " / 涨跌 " + Ui.value(percent(item.changeRate))
                        + " / 持股 " + Ui.value(item.holdingShares10k) + "万股"
                        + " / 市值 " + Ui.value(item.holdingMarketValue10k) + "万元";
                row(label, value);
            }
        }

        section("基金评级");
        if (detail == null || detail.ratings.isEmpty()) {
            row("评级", null);
        } else {
            for (FundRating rating : detail.ratings) {
                row(rating.ratingDate, ratingText(rating));
            }
        }

        section("特色数据");
        if (detail == null || detail.features.isEmpty()) {
            row("特色数据", null);
        } else {
            for (FundFeature feature : detail.features) {
                row(Ui.value(feature.periodLabel) + " " + Ui.value(feature.cutoffDate),
                        "标准差 " + Ui.value(feature.standardDeviation)
                                + " / 夏普 " + Ui.value(feature.sharpeRatio));
            }
        }
    }

    private void openFundDetail() {
        Intent intent = new Intent(this, ProductDetailActivity.class);
        intent.putExtra("fund", fund == null ? fallbackFund() : fund);
        startActivity(intent);
    }

    private Fund fallbackFund() {
        Fund value = new Fund();
        value.fundCode = holding.fundCode;
        value.fundName = holding.fundName;
        return value;
    }

    private void section(String title) {
        TextView view = Ui.text(this, title, 17, Ui.TEXT, Typeface.BOLD);
        view.setPadding(0, Ui.dp(this, 22), 0, Ui.dp(this, 8));
        content.addView(view);
    }

    private void row(String title, Object value) {
        String display = value == null ? null : String.valueOf(value);
        TextView view = Ui.text(this, title + "： " + Ui.value(display), 14, Ui.MUTED, Typeface.NORMAL);
        view.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        content.addView(view);
    }

    private void signedValueRow(String title, Double value) {
        TextView view = Ui.text(this, title + "： " + format(value), 14,
                signedColor(value), Typeface.NORMAL);
        view.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        content.addView(view);
    }

    private void signedPercentRow(String title, Double value) {
        TextView view = Ui.text(this, title + "： " + formatPercent(value), 14,
                signedColor(value), Typeface.NORMAL);
        view.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        content.addView(view);
    }

    private void percentRow(String title, Double value) {
        TextView view = Ui.text(this, title + "： " + formatPercent(value), 14,
                Ui.MUTED, Typeface.NORMAL);
        view.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        content.addView(view);
    }

    private void performanceRow(String title, String value) {
        Double number = toDouble(value);
        if (number == null) {
            row(title, null);
        } else {
            signedPercentRow(title, number);
        }
    }

    private void renderPeriodButtons() {
        HorizontalScrollView scrollView = new HorizontalScrollView(this);
        scrollView.setHorizontalScrollBarEnabled(false);
        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setPadding(0, 0, 0, Ui.dp(this, 8));
        scrollView.addView(buttonRow);
        for (int index = 0; index < PERIOD_KEYS.length; index++) {
            String key = PERIOD_KEYS[index];
            Button button = new Button(this);
            button.setText(PERIOD_LABELS[index]);
            button.setTextSize(12);
            button.setAllCaps(false);
            button.setGravity(Gravity.CENTER);
            button.setTextColor(key.equals(trendPeriod) ? Color.WHITE : Ui.BLUE);
            button.setBackgroundColor(key.equals(trendPeriod)
                    ? Ui.BLUE : Color.rgb(239, 246, 255));
            button.setOnClickListener(view -> {
                trendPeriod = key;
                render();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 38));
            params.setMargins(0, 0, Ui.dp(this, 8), 0);
            buttonRow.addView(button, params);
        }
        content.addView(scrollView);
    }

    private LinearLayout.LayoutParams chartLayoutParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 270));
        params.setMargins(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        return params;
    }

    private String ratingText(FundRating rating) {
        return "招商 " + ratingStars(rating.zhaoshangRating)
                + " / 上海3年 " + ratingStars(rating.shanghaiRating3y)
                + " / 上海5年 " + ratingStars(rating.shanghaiRating5y)
                + " / 济安 " + ratingStars(rating.jianRating)
                + " / 晨星 " + ratingStars(rating.morningStarRating);
    }

    private String ratingStars(Integer value) {
        if (value == null || value <= 0) {
            return "-";
        }
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < Math.min(value, 5); index++) {
            result.append("★");
        }
        return result.toString();
    }

    private String percent(String value) {
        return value == null || value.trim().isEmpty() ? null : value + "%";
    }

    private String preciseValuationDate(String timestamp, String fallbackDate) {
        String formatted = PortfolioHoldingActivity.formatDateTimeSeconds(timestamp);
        return "-".equals(formatted) ? fallbackDate : formatted;
    }

    private String format(Double value) {
        return value == null ? "-" : String.format(Locale.getDefault(), "%.2f", value);
    }

    private String formatPercent(Double value) {
        return value == null ? "-" : String.format(Locale.getDefault(), "%.2f%%", value);
    }

    private int signedColor(Double value) {
        if (value == null || value == 0) {
            return Ui.MUTED;
        }
        return value > 0 ? Ui.RED : Ui.GREEN;
    }

    private Double toDouble(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim().replace("%", "")).doubleValue();
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
