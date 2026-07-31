package com.example.crm.android;

import org.json.JSONObject;

public class PortfolioHoldingBatch {
    public int id;
    public String status;
    public String sourceLabel;
    public String importType;
    public String screenshotDate;
    public int imageCount;
    public int itemCount;
    public int transactionCount;
    public int appliedCount;
    public int skippedCount;
    public String confirmedAt;
    public String createdAt;
    public String updatedAt;

    public static PortfolioHoldingBatch fromJson(JSONObject json) {
        PortfolioHoldingBatch batch = new PortfolioHoldingBatch();
        batch.id = json.optInt("id");
        batch.status = Fund.optionalString(json, "status");
        batch.sourceLabel = Fund.optionalString(json, "sourceLabel");
        batch.importType = Fund.optionalString(json, "importType");
        batch.screenshotDate = Fund.optionalString(json, "screenshotDate");
        batch.imageCount = json.optInt("imageCount");
        batch.itemCount = json.optInt("itemCount");
        batch.transactionCount = json.optInt("transactionCount");
        batch.appliedCount = json.optInt("appliedCount");
        batch.skippedCount = json.optInt("skippedCount");
        batch.confirmedAt = Fund.optionalString(json, "confirmedAt");
        batch.createdAt = Fund.optionalString(json, "createdAt");
        batch.updatedAt = Fund.optionalString(json, "updatedAt");
        return batch;
    }
}
