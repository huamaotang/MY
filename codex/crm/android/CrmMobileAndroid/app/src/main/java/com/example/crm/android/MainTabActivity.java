package com.example.crm.android;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainTabActivity extends Activity {
    private static final int PAGE_SIZE = 20;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<Customer> customers = new ArrayList<>();
    private final List<Fund> funds = new ArrayList<>();
    private final List<FinanceNews> news = new ArrayList<>();
    private final List<StockQuote> stocks = new ArrayList<>();

    private SessionStore session;
    private LinearLayout root;
    private LinearLayout content;
    private LinearLayout listContainer;
    private TextView titleView;
    private TextView summaryView;
    private ProgressBar progressBar;
    private Button loadMoreButton;
    private EditText keywordInput;
    private String activeTab = "customers";
    private int customerPage = 1;
    private int customerTotal = 0;
    private int fundPage = 1;
    private int fundTotal = 0;
    private int stockPage = 1;
    private int stockTotal = 0;
    private boolean loading = false;
    private boolean refreshCustomersOnResume = false;
    private boolean refreshFundsOnResume = false;
    private int newsCategoryTag = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionStore(this);
        if (!session.isAuthenticated()) {
            openLogin();
            return;
        }
        buildShell();
        showCustomers();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (refreshCustomersOnResume && "customers".equals(activeTab)) {
            refreshCustomersOnResume = false;
            reloadCustomers();
        }
        if (refreshFundsOnResume && "funds".equals(activeTab)) {
            refreshFundsOnResume = false;
            reloadFunds();
        }
    }

    private void buildShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 16), Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 10));
        setContentView(root);

        titleView = Ui.text(this, "", 26, Ui.TEXT, Typeface.BOLD);
        root.addView(titleView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 44)));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout tabs = new LinearLayout(this);
        tabs.setGravity(Gravity.CENTER);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(tabs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 52)));
        tabs.addView(tabButton("客户", "customers"), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        tabs.addView(tabButton("产品", "funds"), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        tabs.addView(tabButton("持仓", "portfolio"), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        tabs.addView(tabButton("资讯", "news"), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        tabs.addView(tabButton("我的", "mine"), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
    }

    private Button tabButton(String label, String key) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(view -> {
            if ("customers".equals(key)) {
                showCustomers();
            } else if ("funds".equals(key)) {
                showFunds();
            } else if ("portfolio".equals(key)) {
                showPortfolio();
            } else if ("news".equals(key)) {
                showNews();
            } else {
                showMine();
            }
        });
        return button;
    }

    private void showCustomers() {
        activeTab = "customers";
        titleView.setText("客户");
        buildListContent("搜索客户名称", "搜索", "刷新");
        if (customers.isEmpty()) {
            reloadCustomers();
        } else {
            renderCustomers();
        }
    }

    private void showFunds() {
        activeTab = "funds";
        titleView.setText("产品");
        buildListContent("搜索基金代码或名称", "搜索", "刷新");
        Button stockButton = new Button(this);
        stockButton.setText("切换到股票行情");
        stockButton.setOnClickListener(view -> showStocks());
        content.addView(stockButton, 0, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 44)));
        if (funds.isEmpty()) {
            reloadFunds();
        } else {
            renderFunds();
        }
    }

    private void showStocks() {
        activeTab = "stocks";
        titleView.setText("产品 · 股票");
        buildListContent("搜索股票代码或名称", "搜索", "刷新");
        Button fundButton = new Button(this);
        fundButton.setText("切换到基金");
        fundButton.setOnClickListener(view -> showFunds());
        content.addView(fundButton, 0, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 44)));
        if (stocks.isEmpty()) reloadStocks(); else renderStocks();
    }

    private void showPortfolio() {
        activeTab = "portfolio";
        startActivity(new Intent(this, PortfolioHoldingActivity.class));
    }

    private void showNews() {
        activeTab = "news"; titleView.setText("7×24资讯"); content.removeAllViews();
        android.widget.Spinner categorySpinner = new android.widget.Spinner(this);
        String[] categoryNames = {"全部", "A股", "宏观", "产业", "公司", "数据", "市场", "国际", "观点", "央行", "其他"};
        int[] categoryTags = {-1, 10, 1, 110, 3, 4, 5, 102, 6, 7, 8};
        categorySpinner.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, categoryNames));
        categorySpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                newsCategoryTag = categoryTags[position];
                if (listContainer != null) loadNews();
            }
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        content.addView(categorySpinner);
        progressBar = new ProgressBar(this); content.addView(progressBar);
        ScrollView scroll = new ScrollView(this); listContainer = new LinearLayout(this); listContainer.setOrientation(LinearLayout.VERTICAL); scroll.addView(listContainer); content.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        loadNews();
    }

    private void loadNews() {
        progressBar.setVisibility(View.VISIBLE);
        executor.execute(() -> { try { PageResult<FinanceNews> result=session.apiClient().listFinanceNews(1,100,newsCategoryTag); runOnUiThread(() -> { news.clear(); news.addAll(result.records); progressBar.setVisibility(View.GONE); renderNews(); }); } catch(Exception ex) { runOnUiThread(() -> Ui.toast(this, ex.getMessage())); } });
    }

    private void renderNews() {
        listContainer.removeAllViews();
        for (FinanceNews item : news) { LinearLayout card=card(); card.addView(Ui.text(this, Ui.value(item.createTime)+"  "+Ui.value(item.categoryName), 12, item.categoryTag == 10 ? Ui.RED : Ui.MUTED, Typeface.NORMAL)); card.addView(Ui.text(this, Ui.value(item.content), 15, Ui.TEXT, Typeface.NORMAL)); listContainer.addView(card); }
    }

    private void buildListContent(String hint, String primaryAction, String secondAction) {
        content.removeAllViews();
        keywordInput = new EditText(this);
        keywordInput.setHint(hint);
        keywordInput.setSingleLine(true);
        content.addView(keywordInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 52)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button searchButton = new Button(this);
        searchButton.setText(primaryAction);
        searchButton.setOnClickListener(view -> {
            if ("customers".equals(activeTab)) {
                reloadCustomers();
            } else if ("stocks".equals(activeTab)) {
                reloadStocks();
            } else {
                reloadFunds();
            }
        });
        Button refreshButton = new Button(this);
        refreshButton.setText(secondAction);
        refreshButton.setOnClickListener(view -> searchButton.performClick());
        actions.addView(searchButton, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1));
        actions.addView(refreshButton, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1));
        content.addView(actions);

        summaryView = Ui.text(this, "", 13, Ui.MUTED, Typeface.NORMAL);
        content.addView(summaryView);

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(ProgressBar.GONE);
        content.addView(progressBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 42)));

        ScrollView scrollView = new ScrollView(this);
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(listContainer);
        content.addView(scrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        loadMoreButton = new Button(this);
        loadMoreButton.setText("加载更多");
        loadMoreButton.setOnClickListener(view -> {
            if ("customers".equals(activeTab)) {
                loadCustomers(customerPage + 1);
            } else if ("stocks".equals(activeTab)) {
                loadStocks(stockPage + 1);
            } else {
                loadFunds(fundPage + 1);
            }
        });
        content.addView(loadMoreButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 46)));
    }

    private void reloadCustomers() {
        customerPage = 1;
        customerTotal = 0;
        customers.clear();
        renderCustomers();
        loadCustomers(1);
    }

    private void loadCustomers(int page) {
        if (loading) {
            return;
        }
        loading = true;
        setLoading(true);
        String keyword = keywordInput.getText().toString();
        executor.execute(() -> {
            try {
                PageResult<Customer> result = session.apiClient().listCustomers(page, PAGE_SIZE, keyword);
                runOnUiThread(() -> {
                    customerPage = result.current;
                    customerTotal = result.total;
                    if (page == 1) {
                        customers.clear();
                    }
                    customers.addAll(result.records);
                    loading = false;
                    setLoading(false);
                    renderCustomers();
                });
            } catch (Exception ex) {
                runOnUiThread(() -> handleLoadError(ex));
            }
        });
    }

    private void renderCustomers() {
        listContainer.removeAllViews();
        summaryView.setText("已加载 " + customers.size() + " / " + customerTotal);
        if (customers.isEmpty() && !loading) {
            empty("暂无客户");
        }
        for (Customer customer : customers) {
            listContainer.addView(customerRow(customer));
        }
        loadMoreButton.setVisibility(customers.size() < customerTotal ? View.VISIBLE : View.GONE);
    }

    private View customerRow(Customer customer) {
        LinearLayout card = card();
        card.setOnClickListener(view -> {
            Intent intent = new Intent(this, CustomerDetailActivity.class);
            intent.putExtra("customer", customer);
            refreshCustomersOnResume = true;
            startActivity(intent);
        });
        card.addView(Ui.text(this, Ui.value(customer.customerName), 17, Ui.TEXT, Typeface.BOLD));
        card.addView(Ui.text(this, "状态：" + Ui.value(customer.status), 13, Ui.BLUE, Typeface.BOLD));
        card.addView(Ui.text(this, Ui.value(customer.industry) + "    " + Ui.value(customer.city), 13, Ui.MUTED, Typeface.NORMAL));
        card.addView(Ui.text(this, "电话：" + Ui.value(customer.phone), 13, Ui.MUTED, Typeface.NORMAL));
        return card;
    }

    private void reloadFunds() {
        fundPage = 1;
        fundTotal = 0;
        funds.clear();
        renderFunds();
        loadFunds(1);
    }

    private void loadFunds(int page) {
        if (loading) {
            return;
        }
        loading = true;
        setLoading(true);
        String keyword = keywordInput.getText().toString();
        executor.execute(() -> {
            try {
                PageResult<Fund> result = session.apiClient().listFunds(page, PAGE_SIZE, keyword);
                runOnUiThread(() -> {
                    fundPage = result.current;
                    fundTotal = result.total;
                    if (page == 1) {
                        funds.clear();
                    }
                    funds.addAll(result.records);
                    loading = false;
                    setLoading(false);
                    renderFunds();
                });
            } catch (Exception ex) {
                runOnUiThread(() -> handleLoadError(ex));
            }
        });
    }

    private void renderFunds() {
        listContainer.removeAllViews();
        summaryView.setText("已加载 " + funds.size() + " / " + fundTotal);
        if (funds.isEmpty() && !loading) {
            empty("暂无产品");
        }
        for (Fund fund : funds) {
            listContainer.addView(fundRow(fund));
        }
        loadMoreButton.setVisibility(funds.size() < fundTotal ? View.VISIBLE : View.GONE);
    }

    private View fundRow(Fund fund) {
        LinearLayout card = card();
        card.setOnClickListener(view -> {
            Intent intent = new Intent(this, ProductDetailActivity.class);
            intent.putExtra("fund", fund);
            refreshFundsOnResume = true;
            startActivity(intent);
        });
        card.addView(Ui.text(this, Ui.value(fund.fundName), 17, Ui.TEXT, Typeface.BOLD));
        card.addView(Ui.text(this, "代码：" + Ui.value(fund.fundCode) + "    " + (fund.canBuy ? "可购" : "不可购")
                + "    类型：" + Ui.value(fund.fundType), 13, fund.canBuy ? 0xFF1B8A3A : Ui.MUTED, Typeface.BOLD));
        card.addView(Ui.text(this, "经理：" + Ui.value(fund.fundManager), 13, Ui.MUTED, Typeface.NORMAL));
        if (fund.latestValuation != null) {
            card.addView(ProductDetailActivity.signedPercentView(
                    this, "当日预估", fund.latestValuation.estimatedChangeRate));
            card.addView(Ui.text(this,
                    "估值日期：" + Ui.value(ProductDetailActivity.preciseValuationDate(
                            fund.latestValuation.quoteUpdatedAt,
                            fund.latestValuation.valuationDate))
                            + "    行情覆盖：" + percent(fund.latestValuation.quoteCoverageRate),
                    12, Ui.MUTED, Typeface.NORMAL));
        }
        card.addView(Ui.text(this, "管理人：" + Ui.value(fund.managementCompany), 13, Ui.MUTED, Typeface.NORMAL));
        card.addView(Ui.text(this, "规模：" + Ui.value(fund.netAssetScale), 13, Ui.MUTED, Typeface.NORMAL));
        card.addView(Ui.text(this, "招商：" + ratingStars(fund.latestRating == null ? null : fund.latestRating.zhaoshangRating)
                + "    晨星：" + ratingStars(fund.latestRating == null ? null : fund.latestRating.morningStarRating), 13, Ui.MUTED, Typeface.NORMAL));
        if (fund.latestPerformance != null) {
            addPerformanceRows(card, fund.latestPerformance);
        }
        if (!fund.features.isEmpty()) {
            StringBuilder featureText = new StringBuilder();
            for (FundFeature feature : fund.features) {
                if (featureText.length() > 0) featureText.append(" / ");
                featureText.append(Ui.value(feature.periodLabel)).append(" 标准差:").append(Ui.value(feature.standardDeviation))
                        .append(" 夏普:").append(Ui.value(feature.sharpeRatio));
            }
            card.addView(Ui.text(this, featureText.toString(), 13, Ui.MUTED, Typeface.NORMAL));
        }
        return card;
    }

    private void reloadStocks() {
        stockPage = 1; stockTotal = 0; stocks.clear(); renderStocks(); loadStocks(1);
    }

    private void loadStocks(int page) {
        if (loading) return;
        loading = true; setLoading(true);
        String keyword = keywordInput.getText().toString();
        executor.execute(() -> {
            try {
                PageResult<StockQuote> result = session.apiClient().listStocks(page, PAGE_SIZE, keyword);
                runOnUiThread(() -> {
                    stockPage=result.current; stockTotal=result.total;
                    if(page==1) stocks.clear(); stocks.addAll(result.records);
                    loading=false; setLoading(false); renderStocks();
                });
            } catch(Exception ex) { runOnUiThread(() -> handleLoadError(ex)); }
        });
    }

    private void renderStocks() {
        listContainer.removeAllViews();
        summaryView.setText("已加载 " + stocks.size() + " / " + stockTotal);
        if(stocks.isEmpty()&&!loading) empty("暂无股票行情");
        for(StockQuote stock: stocks) {
            LinearLayout card=card();
            card.setOnClickListener(view -> {
                Intent intent=new Intent(this, StockDetailActivity.class);
                intent.putExtra("stock", stock); startActivity(intent);
            });
            card.addView(Ui.text(this, Ui.value(stock.stockName)+"  "+Ui.value(stock.stockCode),17,Ui.TEXT,Typeface.BOLD));
            int color=signedColor(stock.changeRate);
            card.addView(Ui.text(this,"最新 "+Ui.value(stock.latestPrice)+"    涨跌 "+Ui.value(stock.changeRate)+"%",14,color,Typeface.BOLD));
            card.addView(Ui.text(this,"成交额 "+Ui.value(stock.amount)+"    换手 "+Ui.value(stock.turnoverRate)+"%    量比 "+Ui.value(stock.volumeRatio),13,Ui.MUTED,Typeface.NORMAL));
            card.addView(Ui.text(this,"最后更新时间 "+Ui.value(stock.updatedAt),12,Ui.MUTED,Typeface.NORMAL));
            card.addView(Ui.text(this,"备注 "+Ui.value(stock.comment),12,Ui.MUTED,Typeface.NORMAL));
            card.addView(Ui.text(this,"市盈率 "+Ui.value(stock.peDynamic)+"    市净率 "+Ui.value(stock.pbRatio)+"    "+Ui.value(stock.tradeDate),13,Ui.MUTED,Typeface.NORMAL));
            listContainer.addView(card);
        }
        loadMoreButton.setVisibility(stocks.size()<stockTotal?View.VISIBLE:View.GONE);
    }

    private int signedColor(String value) {
        try { double number=Double.parseDouble(value); return number>0?Ui.RED:number<0?Ui.GREEN:Ui.MUTED; }
        catch(Exception ignored) { return Ui.MUTED; }
    }

    private String percent(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value + "%";
    }

    private String ratingStars(Integer value) {
        if (value == null || value <= 0) return "-";
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < Math.min(value, 5); i++) result.append("★");
        return result.toString();
    }

    private void addPerformanceRows(LinearLayout card, FundPerformance value) {
        card.addView(ProductDetailActivity.signedPercentView(this, "近一周", value.weeklyReturnRate));
        card.addView(ProductDetailActivity.signedPercentView(this, "近一月", value.monthlyReturnRate));
        card.addView(ProductDetailActivity.signedPercentView(this, "近三月", value.threeMonthReturnRate));
        card.addView(ProductDetailActivity.signedPercentView(this, "近六月", value.sixMonthReturnRate));
        card.addView(ProductDetailActivity.signedPercentView(this, "近一年", value.oneYearReturnRate));
        card.addView(ProductDetailActivity.signedPercentView(this, "近两年", value.twoYearReturnRate));
        card.addView(ProductDetailActivity.signedPercentView(this, "近三年", value.threeYearReturnRate));
        card.addView(ProductDetailActivity.signedPercentView(this, "今年以来", value.yearToDateReturnRate));
        card.addView(ProductDetailActivity.signedPercentView(this, "成立以来", value.sinceInceptionReturnRate));
        card.addView(ProductDetailActivity.signedPercentView(this, "区间收益", value.customReturnRate));
        card.addView(ProductDetailActivity.signedPercentView(this, "原手续费", value.originalFeeRate));
        card.addView(ProductDetailActivity.signedPercentView(this, "折后手续费", value.discountedFeeRate));
        card.addView(ProductDetailActivity.signedPercentView(this, "活期宝手续费", value.cashManagementFeeRate));
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        card.setBackgroundColor(0xFFFFFFFF);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        card.setLayoutParams(params);
        return card;
    }

    private void showMine() {
        activeTab = "mine";
        titleView.setText("我的");
        content.removeAllViews();
        content.addView(Ui.text(this, "当前用户：" + Ui.value(session.getUsername()), 17, Ui.TEXT, Typeface.BOLD));
        TextView server = Ui.text(this, "服务器：" + Ui.value(session.getBaseUrl()), 14, Ui.MUTED, Typeface.NORMAL);
        server.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 24));
        content.addView(server);
        Button logoutButton = new Button(this);
        logoutButton.setText("退出登录");
        logoutButton.setOnClickListener(view -> {
            session.logout();
            openLogin();
        });
        content.addView(logoutButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 48)));
    }

    private void empty(String text) {
        TextView empty = Ui.text(this, text, 16, Ui.MUTED, Typeface.NORMAL);
        empty.setGravity(Gravity.CENTER);
        listContainer.addView(empty, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 180)));
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? ProgressBar.VISIBLE : ProgressBar.GONE);
        loadMoreButton.setEnabled(!loading);
    }

    private void handleLoadError(Exception ex) {
        loading = false;
        setLoading(false);
        Ui.toast(this, ex.getMessage());
        if ("customers".equals(activeTab)) {
            renderCustomers();
        } else {
            renderFunds();
        }
    }

    private void openLogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}
