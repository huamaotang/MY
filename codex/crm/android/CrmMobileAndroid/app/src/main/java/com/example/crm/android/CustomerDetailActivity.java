package com.example.crm.android;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CustomerDetailActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private SessionStore session;
    private Customer customer;
    private LinearLayout content;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionStore(this);
        customer = (Customer) getIntent().getSerializableExtra("customer");
        if (customer == null) {
            finish();
            return;
        }
        buildContent();
        render(customer);
        loadDetail();
    }

    private void buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 16), Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 12));
        setContentView(root);

        TextView title = Ui.text(this, "客户详情", 24, Ui.TEXT, Typeface.BOLD);
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
        if (customer.id == null) {
            return;
        }
        progressBar.setVisibility(ProgressBar.VISIBLE);
        executor.execute(() -> {
            try {
                Customer detail = session.apiClient().customerDetail(customer.id);
                runOnUiThread(() -> {
                    progressBar.setVisibility(ProgressBar.GONE);
                    customer = detail;
                    render(detail);
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(ProgressBar.GONE);
                    Ui.toast(this, ex.getMessage());
                });
            }
        });
    }

    private void render(Customer customer) {
        content.removeAllViews();
        content.addView(Ui.text(this, Ui.value(customer.customerName), 22, Ui.TEXT, Typeface.BOLD));
        content.addView(Ui.text(this, "状态：" + Ui.value(customer.status) + "    级别：" + Ui.value(customer.level), 14, Ui.BLUE, Typeface.BOLD));

        section("基础信息");
        row("行业", customer.industry);
        row("客户类型", customer.customerType);
        row("来源", customer.source);
        row("负责人 ID", customer.ownerUserId == null ? null : String.valueOf(customer.ownerUserId));

        section("联系方式");
        row("电话", customer.phone);
        row("邮箱", customer.email);
        row("省份", customer.province);
        row("城市", customer.city);
        row("地址", customer.address);

        section("备注");
        content.addView(Ui.text(this, Ui.value(customer.remark), 15, Ui.TEXT, Typeface.NORMAL));

        section("时间");
        row("创建时间", customer.createdAt);
        row("更新时间", customer.updatedAt);
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
}
