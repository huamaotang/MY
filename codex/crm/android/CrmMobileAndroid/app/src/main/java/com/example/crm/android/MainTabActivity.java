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
    private boolean loading = false;

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
        if (funds.isEmpty()) {
            reloadFunds();
        } else {
            renderFunds();
        }
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
            startActivity(intent);
        });
        card.addView(Ui.text(this, Ui.value(fund.fundName), 17, Ui.TEXT, Typeface.BOLD));
        card.addView(Ui.text(this, "代码：" + Ui.value(fund.fundCode) + "    类型：" + Ui.value(fund.fundType), 13, Ui.BLUE, Typeface.BOLD));
        card.addView(Ui.text(this, "经理：" + Ui.value(fund.fundManager), 13, Ui.MUTED, Typeface.NORMAL));
        card.addView(Ui.text(this, "管理人：" + Ui.value(fund.managementCompany), 13, Ui.MUTED, Typeface.NORMAL));
        return card;
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
