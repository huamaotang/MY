package com.example.crm.android;

import org.json.JSONObject;

public class PortfolioAccountSummary {
    public String sourceLabel;
    public String displayName;
    public int holdingCount;
    public Double holdingAmount;
    public Double holdingProfit;
    public Double holdingReturnRate;
    public Double todayProfit;

    public static PortfolioAccountSummary fromJson(JSONObject json) {
        PortfolioAccountSummary summary = new PortfolioAccountSummary();
        summary.sourceLabel = Fund.optionalString(json, "sourceLabel");
        summary.displayName = Fund.optionalString(json, "displayName");
        summary.holdingCount = json.optInt("holdingCount");
        summary.holdingAmount = number(json, "holdingAmount");
        summary.holdingProfit = number(json, "holdingProfit");
        summary.holdingReturnRate = number(json, "holdingReturnRate");
        summary.todayProfit = number(json, "todayProfit");
        return summary;
    }

    private static Double number(JSONObject json, String key) {
        return !json.has(key) || json.isNull(key) ? null : json.optDouble(key);
    }
}
