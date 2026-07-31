package com.example.crm.android;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class PortfolioHoldingImportPreview {
    public int importId;
    public String sourceLabel;
    public String importType;
    public String status;
    public String screenshotDate;
    public int imageCount;
    public List<String> imageHashes = new ArrayList<>();
    public List<String> warnings = new ArrayList<>();
    public List<PortfolioHoldingImportRow> rows = new ArrayList<>();
    public List<PortfolioTradeAdjustment> tradeAdjustments = new ArrayList<>();

    public static PortfolioHoldingImportPreview fromJson(JSONObject json) {
        PortfolioHoldingImportPreview preview = new PortfolioHoldingImportPreview();
        preview.importId = json.optInt("importId");
        preview.sourceLabel = Fund.optionalString(json, "sourceLabel");
        preview.importType = Fund.optionalString(json, "importType");
        preview.status = Fund.optionalString(json, "status");
        preview.screenshotDate = Fund.optionalString(json, "screenshotDate");
        preview.imageCount = json.optInt("imageCount");
        JSONArray imageHashes = json.optJSONArray("imageHashes");
        if (imageHashes != null) {
            for (int i = 0; i < imageHashes.length(); i++) {
                preview.imageHashes.add(imageHashes.optString(i));
            }
        }
        JSONArray warnings = json.optJSONArray("warnings");
        if (warnings != null) {
            for (int i = 0; i < warnings.length(); i++) {
                preview.warnings.add(warnings.optString(i));
            }
        }
        JSONArray rows = json.optJSONArray("rows");
        if (rows != null) {
            for (int i = 0; i < rows.length(); i++) {
                preview.rows.add(PortfolioHoldingImportRow.fromJson(rows.optJSONObject(i)));
            }
        }
        JSONArray tradeAdjustments = json.optJSONArray("tradeAdjustments");
        if (tradeAdjustments != null) {
            for (int i = 0; i < tradeAdjustments.length(); i++) {
                preview.tradeAdjustments.add(PortfolioTradeAdjustment.fromJson(
                        tradeAdjustments.optJSONObject(i)));
            }
        }
        return preview;
    }
}
