package com.example.crm.android;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoginActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private SessionStore session;
    private EditText serverInput;
    private EditText usernameInput;
    private EditText passwordInput;
    private TextView errorView;
    private Button loginButton;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionStore(this);
        if (session.isAuthenticated()) {
            openCustomerList();
            return;
        }
        buildContent();
    }

    private void buildContent() {
        int padding = Ui.dp(this, 24);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, padding, padding, padding);
        root.setGravity(Gravity.CENTER_VERTICAL);
        setContentView(root);

        TextView title = Ui.text(this, "CRM", 34, Ui.TEXT, Typeface.BOLD);
        TextView subtitle = Ui.text(this, "客户信息移动端", 16, Ui.MUTED, Typeface.NORMAL);
        root.addView(title);
        root.addView(subtitle);

        serverInput = field("服务器地址", InputType.TYPE_TEXT_VARIATION_URI);
        usernameInput = field("用户名", InputType.TYPE_CLASS_TEXT);
        passwordInput = field("密码", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        serverInput.setText(session.getBaseUrl());
        usernameInput.setText("admin");
        passwordInput.setText("admin123");

        addSpacer(root, 24);
        root.addView(serverInput);
        root.addView(usernameInput);
        root.addView(passwordInput);

        errorView = Ui.text(this, "", 13, ColorCompat.RED, Typeface.NORMAL);
        errorView.setVisibility(TextView.GONE);
        root.addView(errorView);

        loginButton = new Button(this);
        loginButton.setText("登录");
        loginButton.setTextColor(ColorCompat.WHITE);
        loginButton.setBackgroundColor(Ui.BLUE);
        loginButton.setOnClickListener(view -> submit());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(this, 48)
        );
        buttonParams.topMargin = Ui.dp(this, 16);
        root.addView(loginButton, buttonParams);

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(ProgressBar.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 36),
                Ui.dp(this, 36)
        );
        progressParams.gravity = Gravity.CENTER_HORIZONTAL;
        progressParams.topMargin = Ui.dp(this, 16);
        root.addView(progressBar, progressParams);

        addSpacer(root, 24);
        TextView hint = Ui.text(this, "真机访问时请填写电脑或服务器的局域网 IP，例如 http://192.168.1.10:8780/api。", 13, Ui.MUTED, Typeface.NORMAL);
        root.addView(hint);
    }

    private EditText field(String hint, int inputType) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setSingleLine(true);
        editText.setInputType(inputType);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(this, 54)
        );
        params.topMargin = Ui.dp(this, 10);
        editText.setLayoutParams(params);
        return editText;
    }

    private void submit() {
        Ui.hideKeyboard(this, loginButton);
        String baseUrl = serverInput.getText().toString().trim();
        String username = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        if (baseUrl.isEmpty() || username.isEmpty() || password.isEmpty()) {
            showError("请填写服务器地址、用户名和密码");
            return;
        }
        setLoading(true);
        executor.execute(() -> {
            try {
                session.apiClient().setBaseUrl(baseUrl);
                LoginResult result = session.apiClient().login(username, password);
                session.saveLogin(baseUrl, result);
                runOnUiThread(this::openCustomerList);
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    setLoading(false);
                    showError(ex.getMessage());
                });
            }
        });
    }

    private void showError(String message) {
        errorView.setText(message == null || message.isEmpty() ? "请求失败" : message);
        errorView.setVisibility(TextView.VISIBLE);
    }

    private void setLoading(boolean loading) {
        loginButton.setEnabled(!loading);
        loginButton.setText(loading ? "登录中" : "登录");
        progressBar.setVisibility(loading ? ProgressBar.VISIBLE : ProgressBar.GONE);
    }

    private void openCustomerList() {
        startActivity(new Intent(this, CustomerListActivity.class));
        finish();
    }

    private void addSpacer(LinearLayout root, int heightDp) {
        TextView spacer = new TextView(this);
        root.addView(spacer, new LinearLayout.LayoutParams(1, Ui.dp(this, heightDp)));
    }

    private static final class ColorCompat {
        static final int RED = 0xFFDC2626;
        static final int WHITE = 0xFFFFFFFF;
    }
}
