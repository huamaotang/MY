package com.example.crm.android;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PortfolioHoldingActivity extends Activity {
    private static final int PICK_IMAGES = 1001;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<Uri> selectedUris = new ArrayList<>();
    private final List<PortfolioHoldingImportRow> previewRows = new ArrayList<>();
    private final List<EditText> codeInputs = new ArrayList<>();
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionStore(this);
        if (!session.isAuthenticated()) {
            finish();
            return;
        }
        buildUi();
        reloadLists();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 16), Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 10));
        setContentView(root);

        titleView = Ui.text(this, "持仓导入", 26, Ui.TEXT, android.graphics.Typeface.BOLD);
        root.addView(titleView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 44)));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

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
        parent.addView(section);
        return section;
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
        statusView.setText("正在识别...");
        executor.execute(() -> {
            try {
                List<byte[]> images = new ArrayList<>();
                for (Uri uri : selectedUris) {
                    images.add(loadJpeg(uri));
                }
                PortfolioHoldingImportPreview preview = session.apiClient().previewPortfolioHoldings(images);
                runOnUiThread(() -> {
                    previewRows.clear();
                    previewRows.addAll(preview.rows);
                    renderPreview(preview);
                    statusView.setText("识别完成，点击确认入库");
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
        previewContainer.addView(Ui.text(this, "批次 " + preview.importId + " · " + preview.imageCount + " 张", 13, Ui.MUTED, android.graphics.Typeface.NORMAL));
        codeInputs.clear();
        for (PortfolioHoldingImportRow row : preview.rows) {
            LinearLayout card = card();
            card.addView(Ui.text(this, "行 " + row.rowNo + " · " + Ui.value(row.fundName), 16, Ui.TEXT, android.graphics.Typeface.BOLD));
            card.addView(Ui.text(this, "金额 " + format(row.holdingAmount), 12, Ui.MUTED, android.graphics.Typeface.NORMAL));
            card.addView(Ui.text(this, "收益 " + format(row.holdingProfit), 12, signedColor(row.holdingProfit), android.graphics.Typeface.NORMAL));
            card.addView(Ui.text(this, "收益率 " + formatPercent(row.holdingReturnRate), 12, signedColor(row.holdingReturnRate), android.graphics.Typeface.NORMAL));
            card.addView(Ui.text(this, "净值成本 " + format(row.costNav), 12, Ui.MUTED, android.graphics.Typeface.NORMAL));
            card.addView(Ui.text(this, "昨日收益 " + format(row.yesterdayProfit), 12, signedColor(row.yesterdayProfit), android.graphics.Typeface.NORMAL));
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
        Button confirm = new Button(this);
        confirm.setText("确认入库");
        confirm.setOnClickListener(v -> confirmPreview(preview));
        previewContainer.addView(confirm);
    }

    private void confirmPreview(PortfolioHoldingImportPreview preview) {
        if (uploading) {
            return;
        }
        for (int i = 0; i < previewRows.size(); i++) {
            EditText input = codeInputs.get(i);
            String code = input.getText().toString().trim();
            if (code.isEmpty()) {
                Ui.toast(this, "第 " + previewRows.get(i).rowNo + " 行缺少基金代码");
                return;
            }
            previewRows.get(i).fundCode = code;
        }
        uploading = true;
        executor.execute(() -> {
            try {
                PortfolioHoldingConfirmRequest request = new PortfolioHoldingConfirmRequest();
                request.screenshotDate = preview.screenshotDate;
                request.items = new ArrayList<>();
                for (PortfolioHoldingImportRow row : previewRows) {
                    PortfolioHoldingConfirmItemRequest item = new PortfolioHoldingConfirmItemRequest();
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
                session.apiClient().confirmPortfolioHoldingImport(preview.importId, request);
                runOnUiThread(() -> {
                    Ui.toast(this, "已入库");
                    previewContainer.removeAllViews();
                    previewRows.clear();
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

    private void loadData() {
        statusView.setText("加载中...");
        executor.execute(() -> {
            try {
                PageResult<UserFundHolding> holdingPage = session.apiClient().listPortfolioHoldings(1, 50, "");
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
        for (UserFundHolding holding : holdings) {
            LinearLayout card = card();
            card.addView(Ui.text(this, Ui.value(holding.fundName), 16, Ui.TEXT, android.graphics.Typeface.BOLD));
            card.addView(Ui.text(this, holding.fundCode + " · 金额 " + format(holding.holdingAmount), 12, Ui.MUTED, android.graphics.Typeface.NORMAL));
            card.addView(Ui.text(this, "当日预估 " + formatPercent(holding.estimatedChangeRate), 12, signedColor(holding.estimatedChangeRate), android.graphics.Typeface.BOLD));
            card.addView(Ui.text(this, "预估盈亏 " + format(holding.estimatedDailyProfit)
                    + " · 估值后金额 " + format(holding.estimatedHoldingAmount), 12,
                    signedColor(holding.estimatedDailyProfit), android.graphics.Typeface.NORMAL));
            card.addView(Ui.text(this,
                    "累计预估 " + formatPercent(holding.estimatedCumulativeChangeRate)
                            + " · 累计盈亏 " + format(holding.estimatedCumulativeProfit),
                    12, signedColor(holding.estimatedCumulativeProfit),
                    android.graphics.Typeface.BOLD));
            card.addView(Ui.text(this, "估值日 " + Ui.value(holding.valuationDate)
                    + " · 行情覆盖 " + formatPercent(holding.valuationCoverageRate), 12,
                    Ui.MUTED, android.graphics.Typeface.NORMAL));
            card.addView(Ui.text(this, "持有收益 " + format(holding.holdingProfit), 12, signedColor(holding.holdingProfit), android.graphics.Typeface.NORMAL));
            card.addView(Ui.text(this, "持有收益率 " + formatPercent(holding.holdingReturnRate), 12, signedColor(holding.holdingReturnRate), android.graphics.Typeface.NORMAL));
            card.addView(Ui.text(this, "净值成本 " + format(holding.costNav), 12, Ui.MUTED, android.graphics.Typeface.NORMAL));
            card.addView(Ui.text(this, "昨日收益 " + format(holding.yesterdayProfit), 12, signedColor(holding.yesterdayProfit), android.graphics.Typeface.NORMAL));
            holdingsContainer.addView(card);
        }
    }

    private void renderImports() {
        importsContainer.removeAllViews();
        for (PortfolioHoldingBatch batch : imports) {
            LinearLayout card = card();
            card.addView(Ui.text(this, "批次 " + batch.id + " · " + batch.status, 15, Ui.TEXT, android.graphics.Typeface.BOLD));
            card.addView(Ui.text(this, "截图 " + Ui.value(batch.screenshotDate) + " · " + batch.itemCount + " 条", 12, Ui.MUTED, android.graphics.Typeface.NORMAL));
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
