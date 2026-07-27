package com.example.crm.android;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProductDetailActivity extends Activity {
    private static final int CHART_NAV_SIZE = 1000;
    private static final String[] PERIOD_KEYS = {"1M", "3M", "6M", "1Y", "3Y", "ALL"};
    private static final String[] PERIOD_LABELS = {"近1月", "近3月", "近6月", "近1年", "近3年", "成立以来"};

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private SessionStore session;
    private Fund fund;
    private FundDetail detail;
    private List<FundNav> chartNavs = new ArrayList<>();
    private String trendPeriod = "1Y";
    private LinearLayout content;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionStore(this);
        fund = (Fund) getIntent().getSerializableExtra("fund");
        if (fund == null) {
            finish();
            return;
        }
        buildContent();
        render();
        loadDetail();
    }

    private void buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 16), Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 12));
        setContentView(root);

        TextView title = Ui.text(this, "产品详情", 24, Ui.TEXT, Typeface.BOLD);
        root.addView(title);

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(ProgressBar.GONE);
        root.addView(progressBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 42)));

        ScrollView scrollView = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(content);
        root.addView(scrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
    }

    private void loadDetail() {
        progressBar.setVisibility(ProgressBar.VISIBLE);
        executor.execute(() -> {
            try {
                FundDetail result = session.apiClient().fundDetail(fund.fundCode);
                PageResult<FundNav> navResult = session.apiClient().listFundNavs(fund.fundCode, 1, CHART_NAV_SIZE);
                runOnUiThread(() -> {
                    progressBar.setVisibility(ProgressBar.GONE);
                    detail = result;
                    chartNavs = navResult.records;
                    if (result.fund != null) {
                        fund = result.fund;
                    }
                    render();
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(ProgressBar.GONE);
                    Ui.toast(this, ex.getMessage());
                });
            }
        });
    }

    private void render() {
        content.removeAllViews();
        content.addView(Ui.text(this, Ui.value(fund.fundName), 22, Ui.TEXT, Typeface.BOLD));
        content.addView(Ui.text(this, "代码：" + Ui.value(fund.fundCode), 14, Ui.BLUE, Typeface.BOLD));

        section("基础信息");
        row("类型", fund.fundType);
        row("基金经理", fund.fundManager);
        row("管理人", fund.managementCompany);
        row("成立日期", fund.inceptionDate);
        row("净资产规模", fund.netAssetScale);
        row("规模截止", fund.scaleDate);
        row("购买状态", fund.canBuy ? "可购买" : "不可购买");

        section("最新净值");
        FundNav nav = detail == null ? null : detail.latestNav;
        row("净值日期", nav == null ? null : nav.navDate);
        row("单位净值", nav == null ? null : nav.unitNav);
        row("累计净值", nav == null ? null : nav.accumulatedNav);
        signedPercentRow("日增长率", nav == null ? null : nav.dailyGrowthRate);

        section("每日估值");
        FundDailyValuation valuation = detail == null ? null : detail.latestValuation;
        row("估值日期", valuation == null ? null
                : preciseValuationDate(valuation.quoteUpdatedAt, valuation.valuationDate));
        signedPercentRow("预估涨跌幅", valuation == null ? null : valuation.estimatedChangeRate);
        row("预估单位净值", valuation == null ? null : valuation.estimatedUnitNav);
        row("基准净值日期", valuation == null ? null : valuation.baseNavDate);
        row("重仓报告日", valuation == null ? null : valuation.holdingReportDate);
        row("重仓占净值", valuation == null ? null : percent(valuation.holdingWeight));
        row("行情覆盖率", valuation == null ? null : percent(valuation.quoteCoverageRate));
        row("行情更新时间", valuation == null ? null : valuation.quoteUpdatedAt);

        section("业绩表现");
        FundPerformance performance = detail == null ? null : detail.latestPerformance;
        signedPercentRow("近一周", performance == null ? null : performance.weeklyReturnRate);
        signedPercentRow("近一月", performance == null ? null : performance.monthlyReturnRate);
        signedPercentRow("近三月", performance == null ? null : performance.threeMonthReturnRate);
        signedPercentRow("近六月", performance == null ? null : performance.sixMonthReturnRate);
        signedPercentRow("近一年", performance == null ? null : performance.oneYearReturnRate);
        signedPercentRow("近两年", performance == null ? null : performance.twoYearReturnRate);
        signedPercentRow("近三年", performance == null ? null : performance.threeYearReturnRate);
        signedPercentRow("今年以来", performance == null ? null : performance.yearToDateReturnRate);
        signedPercentRow("成立以来", performance == null ? null : performance.sinceInceptionReturnRate);
        row("自定义区间", performance == null ? null : Ui.value(performance.customStartDate) + " 至 " + Ui.value(performance.customEndDate));
        signedPercentRow("区间收益", performance == null ? null : performance.customReturnRate);
        signedPercentRow("原手续费", performance == null ? null : performance.originalFeeRate);
        signedPercentRow("折后手续费", performance == null ? null : performance.discountedFeeRate);
        signedPercentRow("活期宝手续费", performance == null ? null : performance.cashManagementFeeRate);

        section("净值与收益走势");
        renderPeriodButtons();
        List<FundTrendChartView.TrendRow> trendRows = buildTrendRows(chartNavs, trendPeriod);
        FundTrendChartView navChart = new FundTrendChartView(this);
        navChart.setData("净值走势图", trendRows, navSeries());
        content.addView(navChart, chartLayoutParams());

        FundTrendChartView returnChart = new FundTrendChartView(this);
        returnChart.setData("收益走势图", trendRows, returnSeries());
        content.addView(returnChart, chartLayoutParams());

        section("持仓摘要");
        if (detail == null || detail.latestHoldings.isEmpty()) {
            row("持仓", null);
        } else {
            int count = Math.min(5, detail.latestHoldings.size());
            for (int i = 0; i < count; i++) {
                FundHolding holding = detail.latestHoldings.get(i);
                signedPercentRow(Ui.value(holding.stockName), holding.netValueRatio);
            }
        }

        section("基金评级");
        if (detail == null || detail.ratings.isEmpty()) {
            row("评级", null);
        } else {
            int count = Math.min(6, detail.ratings.size());
            for (int i = 0; i < count; i++) {
                FundRating rating = detail.ratings.get(i);
                row(rating.ratingDate, ratingText(rating));
            }
        }

        section("特色数据");
        if (detail == null || detail.features.isEmpty()) {
            row("特色数据", null);
        } else {
            int count = Math.min(6, detail.features.size());
            for (int i = 0; i < count; i++) {
                FundFeature feature = detail.features.get(i);
                row(feature.periodLabel + " " + feature.cutoffDate,
                        "标准差 " + Ui.value(feature.standardDeviation) + " / 夏普 " + Ui.value(feature.sharpeRatio));
            }
        }
    }

    private void section(String title) {
        TextView view = Ui.text(this, title, 17, Ui.TEXT, Typeface.BOLD);
        view.setPadding(0, Ui.dp(this, 22), 0, Ui.dp(this, 8));
        content.addView(view);
    }

    private void row(String title, String value) {
        TextView view = Ui.text(this, title + "： " + Ui.value(value), 15, Ui.MUTED, Typeface.NORMAL);
        view.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        content.addView(view);
    }

    private void signedPercentRow(String title, String value) {
        content.addView(signedPercentView(this, title, value));
    }

    static TextView signedPercentView(android.content.Context context, String title, String value) {
        String displayValue = percent(value);
        int color = Ui.MUTED;
        if (value != null && !value.trim().isEmpty()) {
            try {
                int sign = new BigDecimal(value.trim().replace("%", "")).signum();
                color = sign > 0 ? Color.rgb(207, 19, 34) : sign < 0 ? Color.rgb(56, 158, 13) : Ui.TEXT;
            } catch (NumberFormatException ignored) {
                color = Ui.MUTED;
            }
        }
        TextView view = Ui.text(context, title + "： " + Ui.value(displayValue), 15, color, Typeface.NORMAL);
        view.setPadding(0, Ui.dp(context, 4), 0, Ui.dp(context, 4));
        return view;
    }

    private String ratingText(FundRating rating) {
        return "招商 " + ratingStars(rating.zhaoshangRating)
                + " / 上海3年 " + ratingStars(rating.shanghaiRating3y)
                + " / 上海5年 " + ratingStars(rating.shanghaiRating5y)
                + " / 济安 " + ratingStars(rating.jianRating)
                + " / 晨星 " + ratingStars(rating.morningStarRating);
    }

    private static String percent(String value) {
        return value == null || value.isEmpty() ? null : value + "%";
    }

    private String ratingStars(Integer value) {
        if (value == null || value <= 0) {
            return "-";
        }
        int count = Math.min(value, 5);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            builder.append("★");
        }
        return builder.toString();
    }

    private void renderPeriodButtons() {
        HorizontalScrollView scrollView = new HorizontalScrollView(this);
        scrollView.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 0, 0, Ui.dp(this, 8));
        scrollView.addView(row);
        for (int i = 0; i < PERIOD_KEYS.length; i++) {
            String key = PERIOD_KEYS[i];
            Button button = new Button(this);
            button.setText(PERIOD_LABELS[i]);
            button.setTextSize(12);
            button.setAllCaps(false);
            button.setTextColor(key.equals(trendPeriod) ? Color.WHITE : Ui.BLUE);
            button.setBackgroundColor(key.equals(trendPeriod) ? Ui.BLUE : Color.rgb(239, 246, 255));
            button.setOnClickListener(view -> {
                trendPeriod = key;
                render();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 38));
            params.setMargins(0, 0, Ui.dp(this, 8), 0);
            row.addView(button, params);
        }
        content.addView(scrollView);
    }

    private LinearLayout.LayoutParams chartLayoutParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 270));
        params.setMargins(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        return params;
    }

    static List<FundTrendChartView.TrendSeries> navSeries() {
        List<FundTrendChartView.TrendSeries> series = new ArrayList<>();
        series.add(new FundTrendChartView.TrendSeries("unitNav", "单位净值", Ui.BLUE, ""));
        series.add(new FundTrendChartView.TrendSeries("accumulatedNav", "累计净值", Color.rgb(22, 163, 74), ""));
        return series;
    }

    static List<FundTrendChartView.TrendSeries> returnSeries() {
        List<FundTrendChartView.TrendSeries> series = new ArrayList<>();
        series.add(new FundTrendChartView.TrendSeries("returnRate", "累计收益率", Color.rgb(234, 88, 12), "%"));
        return series;
    }

    static List<FundTrendChartView.TrendRow> buildTrendRows(List<FundNav> navs, String period) {
        List<FundNav> sorted = new ArrayList<>();
        for (FundNav nav : navs) {
            if (nav != null && nav.navDate != null && (toDouble(nav.unitNav) != null || toDouble(nav.accumulatedNav) != null)) {
                sorted.add(nav);
            }
        }
        Collections.sort(sorted, (first, second) -> first.navDate.compareTo(second.navDate));
        List<FundNav> filtered = filterTrendPeriod(sorted, period);
        Double base = null;
        for (FundNav nav : filtered) {
            Double value = toDouble(nav.accumulatedNav);
            if (value == null) {
                value = toDouble(nav.unitNav);
            }
            if (value != null && value != 0) {
                base = value;
                break;
            }
        }

        List<FundTrendChartView.TrendRow> rows = new ArrayList<>();
        for (FundNav nav : filtered) {
            FundTrendChartView.TrendRow row = new FundTrendChartView.TrendRow();
            row.date = nav.navDate;
            row.unitNav = toDouble(nav.unitNav);
            row.accumulatedNav = toDouble(nav.accumulatedNav);
            Double returnValue = row.accumulatedNav == null ? row.unitNav : row.accumulatedNav;
            row.returnRate = base != null && returnValue != null ? (returnValue / base - 1) * 100 : null;
            rows.add(row);
        }
        return rows;
    }

    private static List<FundNav> filterTrendPeriod(List<FundNav> navs, String period) {
        if ("ALL".equals(period) || navs.isEmpty()) {
            return navs;
        }
        int months;
        switch (period) {
            case "1M":
                months = 1;
                break;
            case "3M":
                months = 3;
                break;
            case "6M":
                months = 6;
                break;
            case "3Y":
                months = 36;
                break;
            case "1Y":
            default:
                months = 12;
                break;
        }
        int last = dateToInt(navs.get(navs.size() - 1).navDate);
        if (last == 0) {
            return navs;
        }
        int start = shiftMonth(last, -months);
        List<FundNav> filtered = new ArrayList<>();
        for (FundNav nav : navs) {
            int date = dateToInt(nav.navDate);
            if (date == 0 || date >= start) {
                filtered.add(nav);
            }
        }
        return filtered;
    }

    private static int shiftMonth(int yyyymmdd, int monthDelta) {
        int year = yyyymmdd / 10000;
        int month = (yyyymmdd / 100) % 100;
        int day = yyyymmdd % 100;
        int monthIndex = year * 12 + month - 1 + monthDelta;
        int newYear = monthIndex / 12;
        int newMonth = monthIndex % 12 + 1;
        int newDay = Math.min(day, 28);
        return newYear * 10000 + newMonth * 100 + newDay;
    }

    private static int dateToInt(String value) {
        if (value == null || value.length() != 8) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static Double toDouble(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static String preciseValuationDate(String timestamp, String fallbackDate) {
        String formatted = PortfolioHoldingActivity.formatDateTimeSeconds(timestamp);
        return "-".equals(formatted) ? fallbackDate : formatted;
    }
}
