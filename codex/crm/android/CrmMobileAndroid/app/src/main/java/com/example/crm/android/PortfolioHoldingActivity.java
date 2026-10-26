package com.example.crm.android;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PortfolioHoldingActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<UserFundHolding> holdings = new ArrayList<>();
    private final List<Button> tabButtons = new ArrayList<>();
    private final String[] tabTitles = {"账户汇总", "全部", "支付宝", "腾讯理财通"};
    private final String[] scopes = {"all", "all", "alipay", "tencent"};

    private SessionStore session;
    private ScrollView bodyScroll;
    private LinearLayout body;
    private EditText keywordInput;
    private Button amountToggleButton;
    private boolean amountsHidden;
    private boolean dataReady;
    private PortfolioOverview overview;
    private int selectedTab;
    private String sortField = "holdingAmount";
    private String sortOrder = "desc";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionStore(this);
        if (!session.isAuthenticated()) {
            finish();
            return;
        }
        amountsHidden = session.isPortfolioAmountsHidden();
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (session != null && session.isAuthenticated()) {
            amountsHidden = session.isPortfolioAmountsHidden();
            updateAmountToggle();
            loadData();
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 6));
        root.setBackgroundColor(Color.rgb(247, 248, 250));
        setContentView(root);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(Ui.text(this, "持仓", 26, Ui.TEXT, Typeface.BOLD),
                new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        Button importButton = new Button(this);
        importButton.setText("导入");
        importButton.setOnClickListener(v -> openImport(
                selectedTab >= 2 ? scopes[selectedTab] : "alipay", "holding"));
        amountToggleButton = new Button(this);
        amountToggleButton.setAllCaps(false);
        amountToggleButton.setTextSize(13);
        amountToggleButton.setOnClickListener(v -> toggleAmountVisibility());
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 64), Ui.dp(this, 44));
        toggleParams.rightMargin = Ui.dp(this, 6);
        titleRow.addView(amountToggleButton, toggleParams);

        titleRow.addView(importButton, new LinearLayout.LayoutParams(
                Ui.dp(this, 88), Ui.dp(this, 44)));
        root.addView(titleRow);

        HorizontalScrollView tabsScroll = new HorizontalScrollView(this);
        tabsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        for (int index = 0; index < tabTitles.length; index++) {
            int tabIndex = index;
            Button tab = new Button(this);
            tab.setAllCaps(false);
            tab.setText(tabTitles[index]);
            tab.setTextSize(15);
            tab.setOnClickListener(v -> selectTab(tabIndex));
            tabs.addView(tab, new LinearLayout.LayoutParams(
                    index == 3 ? Ui.dp(this, 116) : Ui.dp(this, 84), Ui.dp(this, 42)));
            tabButtons.add(tab);
        }
        tabsScroll.addView(tabs);
        root.addView(tabsScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 46)));

        ScrollView vertical = new ScrollView(this);
        bodyScroll = vertical;
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 10));
        vertical.addView(body);
        root.addView(vertical, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        updateTabStyles();
        updateAmountToggle();
    }

    private void toggleAmountVisibility() {
        amountsHidden = !amountsHidden;
        session.setPortfolioAmountsHidden(amountsHidden);
        updateAmountToggle();
        if (dataReady) {
            int scrollY = bodyScroll == null ? 0 : bodyScroll.getScrollY();
            render();
            if (bodyScroll != null) {
                bodyScroll.post(() -> bodyScroll.scrollTo(0, scrollY));
            }
        }
    }

    private void updateAmountToggle() {
        if (amountToggleButton == null) {
            return;
        }
        amountToggleButton.setText(amountsHidden ? "显示" : "隐藏");
        amountToggleButton.setContentDescription(amountsHidden ? "显示金额" : "隐藏金额");
    }

    private void selectTab(int index) {
        if (selectedTab == index) {
            return;
        }
        selectedTab = index;
        updateTabStyles();
        loadData();
    }

    private void updateTabStyles() {
        for (int index = 0; index < tabButtons.size(); index++) {
            boolean selected = selectedTab == index;
            tabButtons.get(index).setTextColor(selected ? Color.WHITE : Ui.TEXT);
            tabButtons.get(index).setBackgroundColor(selected ? Ui.BLUE : Color.TRANSPARENT);
        }
    }

    private void loadData() {
        dataReady = false;
        body.removeAllViews();
        TextView loading = Ui.text(this, "加载中…", 14, Ui.MUTED, Typeface.NORMAL);
        loading.setGravity(Gravity.CENTER);
        body.addView(loading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 100)));
        int requestedTab = selectedTab;
        executor.execute(() -> {
            try {
                PortfolioOverview loadedOverview = session.apiClient().portfolioOverview();
                List<UserFundHolding> loadedHoldings = new ArrayList<>();
                if (requestedTab > 0) {
                    loadedHoldings.addAll(session.apiClient().listPortfolioHoldings(
                            1, 200, keyword(), scopes[requestedTab], sortField, sortOrder).records);
                }
                runOnUiThread(() -> {
                    if (requestedTab != selectedTab) {
                        return;
                    }
                    overview = loadedOverview;
                    holdings.clear();
                    holdings.addAll(loadedHoldings);
                    dataReady = true;
                    render();
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    body.removeAllViews();
                    body.addView(Ui.text(this, ex.getMessage(), 14, Ui.RED, Typeface.NORMAL));
                });
            }
        });
    }

    private String keyword() {
        return keywordInput == null ? "" : keywordInput.getText().toString().trim();
    }

    private void render() {
        body.removeAllViews();
        if (selectedTab == 0) {
            renderOverview();
        } else {
            renderHoldingList();
        }
    }

    private void renderOverview() {
        if (overview == null || overview.total == null) {
            body.addView(Ui.text(this, "暂无账户数据", 15, Ui.MUTED, Typeface.NORMAL));
            return;
        }
        body.addView(summaryCard(overview.total, true));
        for (PortfolioAccountSummary account : overview.accounts) {
            LinearLayout card = summaryCard(account, false);
            card.setClickable(true);
            card.setOnClickListener(v ->
                    selectTab("tencent".equals(account.sourceLabel) ? 3 : 2));
            body.addView(card);
        }
    }

    private LinearLayout summaryCard(PortfolioAccountSummary summary, boolean prominent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        card.setBackgroundColor(prominent ? Color.rgb(229, 239, 255) : Color.WHITE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = Ui.dp(this, 8);
        card.setLayoutParams(params);

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(Ui.text(this, Ui.value(summary.displayName), prominent ? 21 : 19,
                        Ui.TEXT, Typeface.BOLD),
                new LinearLayout.LayoutParams(0, Ui.dp(this, 32), 1));
        heading.addView(Ui.text(this, summary.holdingCount + " 只" + (prominent ? "" : "  ›"),
                14, Ui.MUTED, Typeface.NORMAL));
        card.addView(heading);
        card.addView(Ui.text(this, amount(summary.holdingAmount), prominent ? 32 : 24,
                Ui.TEXT, Typeface.BOLD));

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.addView(metric("持有收益", amount(summary.holdingProfit), summary.holdingProfit, true),
                new LinearLayout.LayoutParams(0, Ui.dp(this, 50), 1));
        metrics.addView(metric("收益率", percent(summary.holdingReturnRate), summary.holdingReturnRate, false),
                new LinearLayout.LayoutParams(0, Ui.dp(this, 50), 1));
        metrics.addView(metric("今日收益", amount(summary.todayProfit), summary.todayProfit, true),
                new LinearLayout.LayoutParams(0, Ui.dp(this, 50), 1));
        card.addView(metrics);
        return card;
    }

    private LinearLayout metric(String title, String value, Double sign, boolean monetary) {
        LinearLayout metric = new LinearLayout(this);
        metric.setOrientation(LinearLayout.VERTICAL);
        metric.addView(Ui.text(this, title, 13, Ui.MUTED, Typeface.NORMAL));
        metric.addView(Ui.text(this, value, 16,
                monetary && amountsHidden ? Ui.MUTED : signedColor(sign), Typeface.BOLD));
        return metric;
    }

    private void renderHoldingList() {
        String existingKeyword = keyword();
        keywordInput = new EditText(this);
        keywordInput.setHint("搜索基金");
        keywordInput.setSingleLine(true);
        keywordInput.setText(existingKeyword);
        keywordInput.setOnEditorActionListener((view, actionId, event) -> {
            loadData();
            return true;
        });
        body.addView(keywordInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 44)));

        PortfolioAccountSummary summary = selectedSummary();
        if (summary != null) {
            body.addView(summaryStats(summary));
        }
        if (selectedTab >= 2) {
            body.addView(importActions());
        }
        if (holdings.isEmpty()) {
            TextView empty = Ui.text(this,
                    selectedTab >= 2
                            ? "暂无" + (selectedTab == 3 ? "腾讯理财通" : "支付宝")
                            + "持仓\n点击上方“导入持仓列表”上传账户截图"
                            : "暂无持仓",
                    15, Ui.MUTED, Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            body.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 160)));
            return;
        }

        int rowHeightDp = 54;
        LinearLayout tableHost = new LinearLayout(this);
        tableHost.setOrientation(LinearLayout.HORIZONTAL);
        tableHost.setBaselineAligned(false);

        LinearLayout fixedColumn = new LinearLayout(this);
        fixedColumn.setOrientation(LinearLayout.VERTICAL);
        fixedColumn.setBackgroundColor(Color.WHITE);
        fixedColumn.setElevation(Ui.dp(this, 2));
        LinearLayout fixedHeader = tableRow(Color.rgb(238, 240, 244));
        fixedHeader.addView(sortHeader("基金 / 金额", "fundName", 132, Gravity.START));
        fixedColumn.addView(fixedHeader);
        for (int index = 0; index < holdings.size(); index++) {
            fixedColumn.addView(fixedHoldingRow(holdings.get(index), index));
        }

        View columnDivider = new View(this);
        columnDivider.setBackgroundColor(Ui.BORDER);

        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setFillViewport(false);
        horizontal.setHorizontalScrollBarEnabled(false);
        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.VERTICAL);
        LinearLayout metricsHeader = tableRow(Color.rgb(238, 240, 244));
        metricsHeader.addView(sortHeader("当日收益", "estimatedDailyProfit", 92, Gravity.END));
        metricsHeader.addView(sortHeader("类型", "fundType", 76, Gravity.CENTER));
        metricsHeader.addView(sortHeader("持有收益 / 率", "holdingProfit", 116, Gravity.END));
        metrics.addView(metricsHeader);
        for (int index = 0; index < holdings.size(); index++) {
            metrics.addView(metricsHoldingRow(holdings.get(index), index));
        }
        horizontal.addView(metrics);

        tableHost.addView(fixedColumn, new LinearLayout.LayoutParams(
                Ui.dp(this, 132), ViewGroup.LayoutParams.WRAP_CONTENT));
        tableHost.addView(columnDivider, new LinearLayout.LayoutParams(
                Ui.dp(this, 1), Ui.dp(this, rowHeightDp * (holdings.size() + 1))));
        tableHost.addView(horizontal, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        body.addView(tableHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private LinearLayout summaryStats(PortfolioAccountSummary summary) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(Ui.dp(this, 12));
        card.setBackground(background);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = Ui.dp(this, 8);
        card.setLayoutParams(cardParams);

        card.addView(stat("资产", amount(summary.holdingAmount), Ui.TEXT));
        card.addView(statDivider());
        card.addView(stat("今日", amount(summary.todayProfit),
                amountsHidden ? Ui.MUTED : signedColor(summary.todayProfit)));
        card.addView(statDivider());
        card.addView(stat("持仓", summary.holdingCount + " 只", Ui.TEXT));
        return card;
    }

    private LinearLayout stat(String title, String value, int color) {
        LinearLayout stat = new LinearLayout(this);
        stat.setOrientation(LinearLayout.VERTICAL);
        stat.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 10), 0);
        stat.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView label = Ui.text(this, title, 12, Ui.MUTED, Typeface.NORMAL);
        label.setIncludeFontPadding(false);
        stat.addView(label);
        TextView valueView = Ui.text(this, value, 19, color, Typeface.BOLD);
        valueView.setSingleLine(true);
        valueView.setEllipsize(TextUtils.TruncateAt.END);
        if (value.length() > 13) {
            valueView.setTextSize(12);
        } else if (value.length() > 10) {
            valueView.setTextSize(14);
        } else if (value.length() > 8) {
            valueView.setTextSize(16);
        }
        stat.addView(valueView);
        return stat;
    }

    private View statDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(Ui.BORDER);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                Ui.dp(this, 1), Ui.dp(this, 38)));
        return divider;
    }

    private LinearLayout fixedHoldingRow(UserFundHolding holding, int index) {
        LinearLayout row = tableRow(rowBackground(index));
        row.setClickable(true);
        row.setOnClickListener(v -> openHolding(holding));
        row.addView(tableCell(shortFundName(holding.fundName)
                        + "\n" + amount(holding.holdingAmount),
                132, Ui.BLUE, Typeface.BOLD, Gravity.START));
        return row;
    }

    private LinearLayout metricsHoldingRow(UserFundHolding holding, int index) {
        LinearLayout row = tableRow(rowBackground(index));
        row.setClickable(true);
        row.setOnClickListener(v -> openHolding(holding));
        Double daily = holding.estimatedDailyProfit != null
                ? holding.estimatedDailyProfit : holding.todayProfit;
        row.addView(tableCell(amount(daily), 92,
                amountsHidden ? Ui.MUTED : signedColor(daily), Typeface.NORMAL, Gravity.END));
        row.addView(tableCell(Ui.value(holding.fundType), 76,
                Ui.TEXT, Typeface.NORMAL, Gravity.CENTER));
        row.addView(tableCell(amount(holding.holdingProfit) + "\n"
                        + percent(holding.holdingReturnRate),
                116, amountsHidden ? Ui.MUTED : signedColor(holding.holdingProfit),
                Typeface.NORMAL, Gravity.END));
        return row;
    }

    private void openHolding(UserFundHolding holding) {
        Intent intent = new Intent(this, PortfolioHoldingDetailActivity.class);
        intent.putExtra("holding", holding);
        startActivity(intent);
    }

    private int rowBackground(int index) {
        return index % 2 == 0 ? Color.WHITE : Color.rgb(249, 250, 251);
    }

    private LinearLayout importActions() {
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, 0, 0, Ui.dp(this, 4));

        Button holdingButton = new Button(this);
        holdingButton.setAllCaps(false);
        holdingButton.setText("导入持仓列表");
        holdingButton.setTextSize(14);
        holdingButton.setOnClickListener(v -> openImport(scopes[selectedTab], "holding"));

        Button tradeButton = new Button(this);
        tradeButton.setAllCaps(false);
        tradeButton.setText("导入交易记录");
        tradeButton.setTextSize(14);
        tradeButton.setOnClickListener(v -> openImport(scopes[selectedTab], "trade"));

        LinearLayout.LayoutParams holdingParams = new LinearLayout.LayoutParams(
                0, Ui.dp(this, 42), 1);
        holdingParams.rightMargin = Ui.dp(this, 4);
        actions.addView(holdingButton, holdingParams);

        LinearLayout.LayoutParams tradeParams = new LinearLayout.LayoutParams(
                0, Ui.dp(this, 42), 1);
        tradeParams.leftMargin = Ui.dp(this, 4);
        actions.addView(tradeButton, tradeParams);
        return actions;
    }

    private void openImport(String sourceLabel, String selectedImportType) {
        Intent intent = new Intent(this, PortfolioImportActivity.class);
        intent.putExtra("sourceLabel", sourceLabel);
        intent.putExtra("importType", selectedImportType);
        startActivity(intent);
    }

    private PortfolioAccountSummary selectedSummary() {
        if (overview == null) {
            return null;
        }
        if (selectedTab == 1) {
            return overview.total;
        }
        for (PortfolioAccountSummary account : overview.accounts) {
            if (scopes[selectedTab].equals(account.sourceLabel)) {
                return account;
            }
        }
        return null;
    }

    private TextView sortHeader(String title, String field, int width, int gravity) {
        String arrow = sortField.equals(field) ? ("asc".equals(sortOrder) ? " ↑" : " ↓") : "";
        TextView cell = tableCell(title + arrow, width, Ui.TEXT, Typeface.BOLD, gravity);
        cell.setClickable(true);
        cell.setOnClickListener(v -> {
            if (sortField.equals(field)) {
                sortOrder = "asc".equals(sortOrder) ? "desc" : "asc";
            } else {
                sortField = field;
                sortOrder = ("fundName".equals(field) || "fundType".equals(field)) ? "asc" : "desc";
            }
            loadData();
        });
        return cell;
    }

    private LinearLayout tableRow(int backgroundColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(this, 54));
        row.setBackgroundColor(backgroundColor);
        return row;
    }

    private TextView tableCell(String value, int widthDp, int color, int style, int gravity) {
        TextView cell = Ui.text(this, value, 14, color, style);
        cell.setGravity(Gravity.CENTER_VERTICAL | gravity);
        cell.setPadding(Ui.dp(this, 5), Ui.dp(this, 4), Ui.dp(this, 5), Ui.dp(this, 4));
        cell.setMaxLines(2);
        cell.setLayoutParams(new LinearLayout.LayoutParams(
                Ui.dp(this, widthDp), Ui.dp(this, 54)));
        return cell;
    }

    private String amount(Double value) {
        if (value == null) {
            return "-";
        }
        return amountsHidden ? "****" : money(value);
    }

    private String money(Double value) {
        return value == null ? "-" : String.format(Locale.CHINA, "%,.2f", value);
    }

    private String percent(Double value) {
        return value == null ? "-" : String.format(Locale.CHINA, "%+.2f%%", value);
    }

    private int signedColor(Double value) {
        if (value == null || value == 0) {
            return Ui.MUTED;
        }
        return value > 0 ? Ui.RED : Ui.GREEN;
    }

    static String shortFundName(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "-";
        }
        String normalized = value.trim();
        int count = normalized.codePointCount(0, normalized.length());
        if (count <= 8) {
            return normalized;
        }
        return normalized.substring(0, normalized.offsetByCodePoints(0, 8));
    }

    static String valuationDateTime(UserFundHolding holding) {
        if (holding == null) {
            return "-";
        }
        String timestamp = formatDateTimeSeconds(holding.valuationUpdatedAt);
        return "-".equals(timestamp) ? Ui.value(holding.valuationDate) : timestamp;
    }

    static String formatDateTimeSeconds(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "-";
        }
        String normalized = value.trim().replace('T', ' ');
        return normalized.length() > 19 ? normalized.substring(0, 19) : normalized;
    }
}
