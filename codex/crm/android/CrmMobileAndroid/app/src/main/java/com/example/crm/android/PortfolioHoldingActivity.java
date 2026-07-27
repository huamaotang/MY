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
        row.addView(tableCell(format(holding.holdingAmount), 100, Ui.TEXT, Typeface.NORMAL, Gravity.END));
        row.addView(tableCell(formatPercent(holding.estimatedChangeRate), 96,
                signedColor(holding.estimatedChangeRate), Typeface.BOLD, Gravity.END));
        row.addView(tableCell(format(holding.estimatedDailyProfit), 96,
                signedColor(holding.estimatedDailyProfit), Typeface.NORMAL, Gravity.END));
        row.addView(tableCell(format(holding.estimatedHoldingAmount), 108, Ui.TEXT, Typeface.NORMAL, Gravity.END));
        row.addView(tableCell(formatPercent(holding.estimatedCumulativeChangeRate), 96,
                signedColor(holding.estimatedCumulativeChangeRate), Typeface.BOLD, Gravity.END));
        row.addView(tableCell(format(holding.estimatedCumulativeProfit), 96,
                signedColor(holding.estimatedCumulativeProfit), Typeface.NORMAL, Gravity.END));
        row.addView(tableCell(format(holding.holdingProfit), 96,
                signedColor(holding.holdingProfit), Typeface.NORMAL, Gravity.END));
        row.addView(tableCell(formatPercent(holding.holdingReturnRate), 100,
                signedColor(holding.holdingReturnRate), Typeface.NORMAL, Gravity.END));
        row.addView(tableCell(format(holding.holdingCost), 96, Ui.TEXT, Typeface.NORMAL, Gravity.END));
        row.addView(tableCell(format(holding.holdingShares), 96, Ui.TEXT, Typeface.NORMAL, Gravity.END));
        row.addView(tableCell(format(holding.costNav), 92, Ui.TEXT, Typeface.NORMAL, Gravity.END));
        row.addView(tableCell(format(holding.yesterdayProfit), 92,
                signedColor(holding.yesterdayProfit), Typeface.NORMAL, Gravity.END));
        row.addView(tableCell(format(holding.todayProfit), 92,
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
