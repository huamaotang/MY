package com.example.crm.android;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PortfolioImportActivity extends Activity {
    private static final int PICK_IMAGES = 1001;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<Uri> selectedUris = new ArrayList<>();
    private final List<PortfolioHoldingImportRow> previewRows = new ArrayList<>();
    private final List<EditText> codeInputs = new ArrayList<>();
    private final List<PortfolioTradeAdjustment> previewAdjustments = new ArrayList<>();
    private final List<EditText> tradeCodeInputs = new ArrayList<>();
    private final List<UserFundHolding> holdings = new ArrayList<>();
    private final List<PortfolioHoldingBatch> imports = new ArrayList<>();

    private SessionStore session;
    private TextView titleView;
    private LinearLayout content;
    private LinearLayout previewContainer;
    private LinearLayout holdingsContainer;
    private LinearLayout importsContainer;
    private TextView statusView;
    private boolean uploading = false;
    private String sourceLabel = "alipay";
    private String importType = "holding";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionStore(this);
        if (!session.isAuthenticated()) {
            finish();
            return;
        }
        readInitialSelection();
        buildUi();
        reloadLists();
    }

    private void readInitialSelection() {
        Intent intent = getIntent();
        if (intent == null) {
            return;
        }
        String requestedSource = intent.getStringExtra("sourceLabel");
        String requestedType = intent.getStringExtra("importType");
        if ("tencent".equals(requestedSource) || "alipay".equals(requestedSource)) {
            sourceLabel = requestedSource;
        }
        if ("trade".equals(requestedType) || "holding".equals(requestedType)) {
            importType = requestedType;
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 16), Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 10));
        setContentView(root);

        titleView = Ui.text(this, accountName() + ("trade".equals(importType)
                ? "交易记录导入" : "持仓列表导入"), 26, Ui.TEXT,
                android.graphics.Typeface.BOLD);
        root.addView(titleView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 44)));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        Button sourceButton = new Button(this);
        sourceButton.setText("账户：" + accountName());
        sourceButton.setOnClickListener(v -> {
            sourceLabel = "alipay".equals(sourceLabel) ? "tencent" : "alipay";
            sourceButton.setText("账户：" + accountName());
            updateTitle();
            reloadLists();
        });
        content.addView(sourceButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 44)));

        Button importTypeButton = new Button(this);
        importTypeButton.setText("导入类型：" + (
                "trade".equals(importType) ? "交易记录" : "持仓列表"));
        importTypeButton.setOnClickListener(v -> {
            importType = "holding".equals(importType) ? "trade" : "holding";
            importTypeButton.setText("导入类型：" + (
                    "trade".equals(importType) ? "交易记录" : "持仓列表"));
            updateTitle();
        });
        content.addView(importTypeButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 44)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        Button pickButton = new Button(this);
        pickButton.setText("选择截图");
        pickButton.setOnClickListener(v -> pickImages());
        Button uploadButton = new Button(this);
        uploadButton.setText("上传识别");
        uploadButton.setOnClickListener(v -> uploadSelectedImages());
        Button refreshButton = new Button(this);
        refreshButton.setText("刷新");
        refreshButton.setOnClickListener(v -> reloadLists());
        actions.addView(pickButton, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1));
        actions.addView(uploadButton, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1));
        actions.addView(refreshButton, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1));
        content.addView(actions);

        statusView = Ui.text(this, "", 13, Ui.MUTED, android.graphics.Typeface.NORMAL);
        content.addView(statusView);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(body);
        content.addView(scrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        previewContainer = section(body, "识别预览");
        holdingsContainer = section(body, "当前持仓");
        importsContainer = section(body, "导入历史");
    }

    private LinearLayout section(LinearLayout parent, String title) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
        section.addView(Ui.text(this, title, 18, Ui.TEXT, android.graphics.Typeface.BOLD));
        LinearLayout sectionContent = new LinearLayout(this);
        sectionContent.setOrientation(LinearLayout.VERTICAL);
        section.addView(sectionContent);
        parent.addView(section);
        return sectionContent;
    }

    private void pickImages() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(intent, "选择截图"), PICK_IMAGES);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_IMAGES || resultCode != RESULT_OK || data == null) {
            return;
        }
        selectedUris.clear();
        if (data.getClipData() != null) {
            int count = data.getClipData().getItemCount();
            for (int i = 0; i < count && selectedUris.size() < 3; i++) {
                selectedUris.add(data.getClipData().getItemAt(i).getUri());
            }
        } else if (data.getData() != null) {
            selectedUris.add(data.getData());
        }
        Ui.toast(this, "已选择 " + selectedUris.size() + " 张截图");
    }

    private void uploadSelectedImages() {
        if (uploading) {
            return;
        }
        if (selectedUris.isEmpty()) {
            Ui.toast(this, "请先选择截图");
            return;
        }
        uploading = true;
        String selectedSourceLabel = sourceLabel;
        String selectedImportType = importType;
        statusView.setText("正在识别...");
        executor.execute(() -> {
            try {
                List<byte[]> images = new ArrayList<>();
                for (Uri uri : selectedUris) {
                    images.add(loadJpeg(uri));
                }
                PortfolioHoldingImportPreview preview = session.apiClient()
                        .previewPortfolioHoldings(
                                images, selectedSourceLabel, selectedImportType);
                runOnUiThread(() -> {
                    previewRows.clear();
                    previewRows.addAll(preview.rows);
                    previewAdjustments.clear();
                    previewAdjustments.addAll(preview.tradeAdjustments);
                    renderPreview(preview);
                    statusView.setText("识别完成，请核对后确认");
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    statusView.setText(ex.getMessage());
                    Ui.toast(this, ex.getMessage());
                });
            } finally {
                uploading = false;
            }
        });
    }

    private byte[] loadJpeg(Uri uri) throws Exception {
        InputStream input = getContentResolver().openInputStream(uri);
        if (input == null) {
            throw new IllegalStateException("无法读取图片");
        }
        Bitmap bitmap = BitmapFactory.decodeStream(input);
        input.close();
        if (bitmap == null) {
            throw new IllegalStateException("图片解码失败");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output);
        return output.toByteArray();
    }

    private void renderPreview(PortfolioHoldingImportPreview preview) {
        previewContainer.removeAllViews();
        String type = "trade".equals(preview.importType) ? "交易记录" : "持仓列表";
        previewContainer.addView(Ui.text(this,
                "批次 " + preview.importId + " · " + type + " · "
                        + preview.imageCount + " 张",
                13, Ui.MUTED, android.graphics.Typeface.NORMAL));
        for (String warning : preview.warnings) {
            previewContainer.addView(Ui.text(this, "提示：" + warning, 12, Ui.RED,
                    android.graphics.Typeface.NORMAL));
        }
        if ("trade".equals(preview.importType)) {
            renderTradePreview(preview);
        } else {
            renderHoldingPreview(preview);
        }
        Button confirm = new Button(this);
        confirm.setText("trade".equals(preview.importType) ? "确认调整持仓" : "确认覆盖持仓");
        confirm.setOnClickListener(v -> confirmPreview(preview));
        previewContainer.addView(confirm);
    }

    private void renderHoldingPreview(PortfolioHoldingImportPreview preview) {
        codeInputs.clear();
        tradeCodeInputs.clear();
        for (PortfolioHoldingImportRow row : preview.rows) {
            LinearLayout card = card();
            card.addView(Ui.text(this, "行 " + row.rowNo + " · " + Ui.value(row.fundName), 16, Ui.TEXT, android.graphics.Typeface.BOLD));
            card.addView(Ui.text(this, "金额 " + money(row.holdingAmount), 12, Ui.MUTED, android.graphics.Typeface.NORMAL));
            card.addView(Ui.text(this, "收益 " + money(row.holdingProfit), 12, signedColor(row.holdingProfit), android.graphics.Typeface.NORMAL));
            card.addView(Ui.text(this, "收益率 " + formatPercent(row.holdingReturnRate), 12, signedColor(row.holdingReturnRate), android.graphics.Typeface.NORMAL));
            card.addView(Ui.text(this, "净值成本 " + format(row.costNav), 12, Ui.MUTED, android.graphics.Typeface.NORMAL));
            card.addView(Ui.text(this, "昨日收益 " + money(row.yesterdayProfit), 12, signedColor(row.yesterdayProfit), android.graphics.Typeface.NORMAL));
            EditText codeInput = new EditText(this);
            codeInput.setHint("基金代码");
            codeInput.setText(row.fundCode == null ? "" : row.fundCode);
            card.addView(codeInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 48)));
            if (!row.candidates.isEmpty()) {
                StringBuilder candidateText = new StringBuilder("候选：");
                for (PortfolioHoldingCandidate candidate : row.candidates) {
                    candidateText.append(candidate.fundCode).append(" ").append(candidate.fundName).append(" ");
                }
                card.addView(Ui.text(this, candidateText.toString(), 12, Ui.MUTED, android.graphics.Typeface.NORMAL));
            }
            previewContainer.addView(card);
            codeInputs.add(codeInput);
        }
    }

    private void renderTradePreview(PortfolioHoldingImportPreview preview) {
        codeInputs.clear();
        tradeCodeInputs.clear();
        if (preview.tradeAdjustments.isEmpty()) {
            previewContainer.addView(Ui.text(this, "未识别到可预览的基金交易", 14, Ui.MUTED,
                    android.graphics.Typeface.NORMAL));
            return;
        }
        for (PortfolioTradeAdjustment adjustment : preview.tradeAdjustments) {
            LinearLayout card = card();
            card.addView(Ui.text(this, Ui.value(adjustment.fundName), 16, Ui.TEXT,
                    android.graphics.Typeface.BOLD));
            card.addView(Ui.text(this,
                    "买入 " + money(adjustment.buyAmount)
                            + " · 卖出 " + money(adjustment.sellAmount)
                            + " · 净额 " + money(adjustment.netAmount),
                    12, signedColor(adjustment.netAmount), android.graphics.Typeface.NORMAL));
            card.addView(Ui.text(this,
                    "当前 " + money(adjustment.currentHoldingAmount)
                            + " → 预计 " + money(adjustment.projectedHoldingAmount),
                    12, Ui.MUTED, android.graphics.Typeface.NORMAL));
            card.addView(Ui.text(this,
                    "交易 " + adjustment.transactionCount
                            + " 笔 · 跳过 " + adjustment.skippedCount
                            + " 笔 · " + (adjustment.applicable ? "可应用" : "不可应用"),
                    12, adjustment.applicable ? Ui.BLUE : Ui.RED,
                    android.graphics.Typeface.NORMAL));
            for (String warning : adjustment.warnings) {
                card.addView(Ui.text(this, "提示：" + warning, 12, Ui.RED,
                        android.graphics.Typeface.NORMAL));
            }
            EditText codeInput = new EditText(this);
            codeInput.setHint("映射到同平台已有基金代码；留空则跳过");
            codeInput.setText(adjustment.fundCode == null ? "" : adjustment.fundCode);
            card.addView(codeInput, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 48)));
            if (!adjustment.candidates.isEmpty()) {
                StringBuilder candidateText = new StringBuilder("可选持仓：");
                for (PortfolioHoldingCandidate candidate : adjustment.candidates) {
                    candidateText.append(candidate.fundCode)
                            .append(" ")
                            .append(candidate.fundName)
                            .append(" ");
                }
                card.addView(Ui.text(this, candidateText.toString(), 12, Ui.MUTED,
                        android.graphics.Typeface.NORMAL));
            }
            previewContainer.addView(card);
            tradeCodeInputs.add(codeInput);
        }
    }

    private void confirmPreview(PortfolioHoldingImportPreview preview) {
        if (uploading) {
            return;
        }
        boolean tradeImport = "trade".equals(preview.importType);
        if (!tradeImport) {
            for (int i = 0; i < previewRows.size(); i++) {
                EditText input = codeInputs.get(i);
                String code = input.getText().toString().trim();
                if (code.isEmpty()) {
                    Ui.toast(this, "第 " + previewRows.get(i).rowNo + " 行缺少基金代码");
                    return;
                }
                previewRows.get(i).fundCode = code;
            }
        }
        uploading = true;
        executor.execute(() -> {
            try {
                PortfolioHoldingConfirmRequest request = new PortfolioHoldingConfirmRequest();
                request.screenshotDate = preview.screenshotDate;
                if (tradeImport) {
                    request.tradeMappings = new ArrayList<>();
                    for (int i = 0; i < previewAdjustments.size(); i++) {
                        String code = tradeCodeInputs.get(i).getText().toString().trim();
                        if (code.isEmpty()) {
                            continue;
                        }
                        PortfolioTradeMappingRequest mapping = new PortfolioTradeMappingRequest();
                        mapping.groupKey = previewAdjustments.get(i).groupKey;
                        mapping.fundCode = code;
                        request.tradeMappings.add(mapping);
                    }
                } else {
                    request.items = new ArrayList<>();
                    for (PortfolioHoldingImportRow row : previewRows) {
                        PortfolioHoldingConfirmItemRequest item =
                                new PortfolioHoldingConfirmItemRequest();
                        item.rowNo = row.rowNo;
                        item.fundCode = row.fundCode;
                        item.fundName = row.fundName;
                        item.holdingAmount = row.holdingAmount;
                        item.holdingProfit = row.holdingProfit;
                        item.holdingReturnRate = row.holdingReturnRate;
                        item.holdingCost = row.holdingCost;
                        item.yesterdayProfit = row.yesterdayProfit;
                        item.todayProfit = row.todayProfit;
                        item.holdingShares = row.holdingShares;
                        item.costNav = row.costNav;
                        item.screenshotDate = row.screenshotDate;
                        item.confidence = row.confidence;
                        item.rawTexts = row.rawTexts;
                        request.items.add(item);
                    }
                }
                PortfolioHoldingConfirmResponse result = session.apiClient()
                        .confirmPortfolioHoldingImport(preview.importId, request);
                runOnUiThread(() -> {
                    String message = "已影响 " + result.affectedHoldingCount + " 只基金";
                    if (tradeImport) {
                        message += "，应用 " + result.appliedTransactionCount
                                + " 笔，跳过 " + result.skippedTransactionCount + " 笔";
                    }
                    if (!result.warnings.isEmpty()) {
                        message += "；" + result.warnings.get(0);
                    }
                    Ui.toast(this, message);
                    previewContainer.removeAllViews();
                    previewRows.clear();
                    previewAdjustments.clear();
                    codeInputs.clear();
                    tradeCodeInputs.clear();
                    loadData();
                });
            } catch (Exception ex) {
                runOnUiThread(() -> Ui.toast(this, ex.getMessage()));
            } finally {
                uploading = false;
            }
        });
    }

    private void reloadLists() {
        loadData();
    }

    private String accountName() {
        return "tencent".equals(sourceLabel) ? "腾讯理财通" : "支付宝";
    }

    private void updateTitle() {
        if (titleView != null) {
            titleView.setText(accountName() + ("trade".equals(importType)
                    ? "交易记录导入" : "持仓列表导入"));
        }
    }

    private void loadData() {
        statusView.setText("加载中...");
        executor.execute(() -> {
            try {
                PageResult<UserFundHolding> holdingPage = session.apiClient()
                        .listPortfolioHoldings(
                                1, 50, "", sourceLabel, "holdingAmount", "desc");
                PageResult<PortfolioHoldingBatch> importPage = session.apiClient().listPortfolioImports(1, 20);
                runOnUiThread(() -> {
                    holdings.clear();
                    holdings.addAll(holdingPage.records);
                    imports.clear();
                    imports.addAll(importPage.records);
                    renderHoldings();
                    renderImports();
                    statusView.setText("共 " + holdings.size() + " 条持仓");
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    statusView.setText(ex.getMessage());
                    Ui.toast(this, ex.getMessage());
                });
            }
        });
    }

    private void renderHoldings() {
        holdingsContainer.removeAllViews();
        TextView hint = Ui.text(this, "左右滑动查看全部数据，点击一行查看持仓详情", 12, Ui.MUTED, Typeface.NORMAL);
        hint.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 10));
        holdingsContainer.addView(hint);
        if (holdings.isEmpty()) {
            TextView empty = Ui.text(this, "暂无持仓", 14, Ui.MUTED, Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            holdingsContainer.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 96)));
            return;
        }

        HorizontalScrollView horizontalScroll = new HorizontalScrollView(this);
        horizontalScroll.setFillViewport(true);
        horizontalScroll.setHorizontalScrollBarEnabled(true);
        LinearLayout table = new LinearLayout(this);
        table.setOrientation(LinearLayout.VERTICAL);
        table.addView(holdingHeaderRow());
        for (int index = 0; index < holdings.size(); index++) {
            table.addView(holdingTableRow(holdings.get(index), index));
        }
        horizontalScroll.addView(table);
        holdingsContainer.addView(horizontalScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private LinearLayout holdingHeaderRow() {
        LinearLayout row = tableRow(Color.rgb(243, 244, 246));
        row.addView(tableCell("基金", 124, Ui.TEXT, Typeface.BOLD, Gravity.START));
        row.addView(tableCell("持有金额", 100, Ui.TEXT, Typeface.BOLD, Gravity.END));
        row.addView(tableCell("当日预估", 96, Ui.TEXT, Typeface.BOLD, Gravity.END));
        row.addView(tableCell("预估盈亏", 96, Ui.TEXT, Typeface.BOLD, Gravity.END));
        row.addView(tableCell("估值后金额", 108, Ui.TEXT, Typeface.BOLD, Gravity.END));
        row.addView(tableCell("累计预估", 96, Ui.TEXT, Typeface.BOLD, Gravity.END));
        row.addView(tableCell("累计盈亏", 96, Ui.TEXT, Typeface.BOLD, Gravity.END));
        row.addView(tableCell("持有收益", 96, Ui.TEXT, Typeface.BOLD, Gravity.END));
        row.addView(tableCell("持有收益率", 100, Ui.TEXT, Typeface.BOLD, Gravity.END));
        row.addView(tableCell("持有成本", 96, Ui.TEXT, Typeface.BOLD, Gravity.END));
        row.addView(tableCell("持有份额", 96, Ui.TEXT, Typeface.BOLD, Gravity.END));
        row.addView(tableCell("净值成本", 92, Ui.TEXT, Typeface.BOLD, Gravity.END));
        row.addView(tableCell("昨日收益", 92, Ui.TEXT, Typeface.BOLD, Gravity.END));
        row.addView(tableCell("今日收益", 92, Ui.TEXT, Typeface.BOLD, Gravity.END));
        row.addView(tableCell("行情覆盖", 92, Ui.TEXT, Typeface.BOLD, Gravity.END));
        row.addView(tableCell("估值日期", 148, Ui.TEXT, Typeface.BOLD, Gravity.CENTER));
        return row;
    }

    private LinearLayout holdingTableRow(UserFundHolding holding, int index) {
        LinearLayout row = tableRow(index % 2 == 0 ? Color.WHITE : Color.rgb(249, 250, 251));
        row.setClickable(true);
        row.setOnClickListener(view -> openHoldingDetail(holding));
        row.addView(tableCell(shortFundName(holding.fundName) + "\n" + Ui.value(holding.fundCode),
                124, Ui.BLUE, Typeface.BOLD, Gravity.START));
        row.addView(tableCell(money(holding.holdingAmount), 100, Ui.TEXT, Typeface.NORMAL, Gravity.END));
        row.addView(tableCell(formatPercent(holding.estimatedChangeRate), 96,
                signedColor(holding.estimatedChangeRate), Typeface.BOLD, Gravity.END));
        row.addView(tableCell(money(holding.estimatedDailyProfit), 96,
                signedColor(holding.estimatedDailyProfit), Typeface.NORMAL, Gravity.END));
        row.addView(tableCell(money(holding.estimatedHoldingAmount), 108, Ui.TEXT, Typeface.NORMAL, Gravity.END));
        row.addView(tableCell(formatPercent(holding.estimatedCumulativeChangeRate), 96,
                signedColor(holding.estimatedCumulativeChangeRate), Typeface.BOLD, Gravity.END));
        row.addView(tableCell(money(holding.estimatedCumulativeProfit), 96,
                signedColor(holding.estimatedCumulativeProfit), Typeface.NORMAL, Gravity.END));
        row.addView(tableCell(money(holding.holdingProfit), 96,
                signedColor(holding.holdingProfit), Typeface.NORMAL, Gravity.END));
        row.addView(tableCell(formatPercent(holding.holdingReturnRate), 100,
                signedColor(holding.holdingReturnRate), Typeface.NORMAL, Gravity.END));
        row.addView(tableCell(money(holding.holdingCost), 96, Ui.TEXT, Typeface.NORMAL, Gravity.END));
        row.addView(tableCell(format(holding.holdingShares), 96, Ui.TEXT, Typeface.NORMAL, Gravity.END));
        row.addView(tableCell(format(holding.costNav), 92, Ui.TEXT, Typeface.NORMAL, Gravity.END));
        row.addView(tableCell(money(holding.yesterdayProfit), 92,
                signedColor(holding.yesterdayProfit), Typeface.NORMAL, Gravity.END));
        row.addView(tableCell(money(holding.todayProfit), 92,
                signedColor(holding.todayProfit), Typeface.NORMAL, Gravity.END));
        row.addView(tableCell(formatPercent(holding.valuationCoverageRate), 92,
                Ui.MUTED, Typeface.NORMAL, Gravity.END));
        row.addView(tableCell(valuationDateTime(holding), 148,
                Ui.MUTED, Typeface.NORMAL, Gravity.CENTER));
        return row;
    }

    private LinearLayout tableRow(int backgroundColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(this, 58));
        row.setBackgroundColor(backgroundColor);
        return row;
    }

    private TextView tableCell(String value, int widthDp, int color, int style, int gravity) {
        TextView cell = Ui.text(this, value, 12, color, style);
        cell.setGravity(Gravity.CENTER_VERTICAL | gravity);
        cell.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        cell.setMaxLines(2);
        cell.setMinHeight(Ui.dp(this, 58));
        cell.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, widthDp), ViewGroup.LayoutParams.MATCH_PARENT));
        return cell;
    }

    private void openHoldingDetail(UserFundHolding holding) {
        Intent intent = new Intent(this, PortfolioHoldingDetailActivity.class);
        intent.putExtra("holding", holding);
        startActivity(intent);
    }

    static String shortFundName(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "-";
        }
        String normalized = value.trim();
        int count = normalized.codePointCount(0, normalized.length());
        if (count <= 6) {
            return normalized;
        }
        int end = normalized.offsetByCodePoints(0, 6);
        return normalized.substring(0, end) + "...";
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

    private void renderImports() {
        importsContainer.removeAllViews();
        for (PortfolioHoldingBatch batch : imports) {
            if (!sourceLabel.equals(batch.sourceLabel)) {
                continue;
            }
            LinearLayout card = card();
            card.addView(Ui.text(this, "批次 " + batch.id + " · " + batch.status, 15, Ui.TEXT, android.graphics.Typeface.BOLD));
            String source = "tencent".equals(batch.sourceLabel) ? "腾讯理财通" : "支付宝";
            String type = "trade".equals(batch.importType) ? "交易记录" : "持仓列表";
            String stats = "trade".equals(batch.importType)
                    ? "基金 " + batch.itemCount + " · 交易 " + batch.transactionCount
                    + " · 应用 " + batch.appliedCount + " · 跳过 " + batch.skippedCount
                    : "基金 " + batch.itemCount;
            card.addView(Ui.text(this,
                    source + " · " + type + " · 截图 " + Ui.value(batch.screenshotDate)
                            + " · " + stats,
                    12, Ui.MUTED, android.graphics.Typeface.NORMAL));
            importsContainer.addView(card);
        }
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        card.setBackgroundColor(0xffffffff);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = Ui.dp(this, 10);
        card.setLayoutParams(params);
        return card;
    }

    private String format(Double value) {
        return value == null ? "-" : String.valueOf(value);
    }

    private String money(Double value) {
        return value == null ? "-" : String.format(java.util.Locale.CHINA, "%,.2f", value);
    }

    private String formatPercent(Double value) {
        return value == null ? "-" : String.valueOf(value) + "%";
    }

    private int signedColor(Double value) {
        if (value == null || value == 0) {
            return Ui.MUTED;
        }
        return value > 0 ? Ui.RED : Ui.GREEN;
    }
}
