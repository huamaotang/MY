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

public class CustomerListActivity extends Activity {
    private static final int PAGE_SIZE = 20;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<Customer> customers = new ArrayList<>();

    private SessionStore session;
    private EditText keywordInput;
    private LinearLayout listContainer;
    private TextView summaryView;
    private ProgressBar progressBar;
    private Button loadMoreButton;
    private int currentPage = 1;
    private int total = 0;
    private boolean loading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionStore(this);
        if (!session.isAuthenticated()) {
            openLogin();
            return;
        }
        buildContent();
        reload();
    }

    private void buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 16), Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 12));
        setContentView(root);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 48)));

        TextView title = Ui.text(this, "客户", 26, Ui.TEXT, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button logoutButton = new Button(this);
        logoutButton.setText("退出");
        logoutButton.setOnClickListener(view -> {
            session.logout();
            openLogin();
        });
        header.addView(logoutButton);

        keywordInput = new EditText(this);
        keywordInput.setHint("搜索客户名称");
        keywordInput.setSingleLine(true);
        root.addView(keywordInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 52)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button searchButton = new Button(this);
        searchButton.setText("搜索");
        searchButton.setOnClickListener(view -> reload());
        Button refreshButton = new Button(this);
        refreshButton.setText("刷新");
        refreshButton.setOnClickListener(view -> reload());
        actions.addView(searchButton, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1));
        actions.addView(refreshButton, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1));
        root.addView(actions);

        summaryView = Ui.text(this, "", 13, Ui.MUTED, Typeface.NORMAL);
        root.addView(summaryView);

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(ProgressBar.GONE);
        root.addView(progressBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 42)));

        ScrollView scrollView = new ScrollView(this);
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(listContainer);
        root.addView(scrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        loadMoreButton = new Button(this);
        loadMoreButton.setText("加载更多");
        loadMoreButton.setOnClickListener(view -> load(currentPage + 1));
        root.addView(loadMoreButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 46)));
    }

    private void reload() {
        currentPage = 1;
        total = 0;
        customers.clear();
        renderList();
        load(1);
    }

    private void load(int page) {
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
                    currentPage = result.current;
                    total = result.total;
                    if (page == 1) {
                        customers.clear();
                    }
                    customers.addAll(result.records);
                    loading = false;
                    setLoading(false);
                    renderList();
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    loading = false;
                    setLoading(false);
                    Ui.toast(this, ex.getMessage());
                    renderList();
                });
            }
        });
    }

    private void renderList() {
        listContainer.removeAllViews();
        summaryView.setText("当前用户：" + session.getUsername() + "    已加载 " + customers.size() + " / " + total);
        if (customers.isEmpty() && !loading) {
            TextView empty = Ui.text(this, "暂无客户\n尝试调整搜索关键词", 16, Ui.MUTED, Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            listContainer.addView(empty, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 180)));
        }
        for (Customer customer : customers) {
            listContainer.addView(row(customer));
        }
        loadMoreButton.setVisibility(customers.size() < total ? View.VISIBLE : View.GONE);
    }

    private View row(Customer customer) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        card.setBackgroundColor(0xFFFFFFFF);
        card.setOnClickListener(view -> {
            Intent intent = new Intent(this, CustomerDetailActivity.class);
            intent.putExtra("customer", customer);
            startActivity(intent);
        });

        TextView name = Ui.text(this, Ui.value(customer.customerName), 17, Ui.TEXT, Typeface.BOLD);
        TextView status = Ui.text(this, "状态：" + Ui.value(customer.status), 13, Ui.BLUE, Typeface.BOLD);
        TextView meta = Ui.text(this,
                Ui.value(customer.industry) + "    " + Ui.value(customer.city),
                13,
                Ui.MUTED,
                Typeface.NORMAL);
        TextView phone = Ui.text(this, "电话：" + Ui.value(customer.phone), 13, Ui.MUTED, Typeface.NORMAL);
        card.addView(name);
        card.addView(status);
        card.addView(meta);
        card.addView(phone);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        card.setLayoutParams(params);
        return card;
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? ProgressBar.VISIBLE : ProgressBar.GONE);
        loadMoreButton.setEnabled(!loading);
    }

    private void openLogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}
