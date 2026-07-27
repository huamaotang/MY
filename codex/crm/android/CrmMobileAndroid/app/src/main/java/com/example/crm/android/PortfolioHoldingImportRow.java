package com.example.crm.android;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class PortfolioHoldingImportRow {
    public int rowNo;
    public String fundCode;
    public String fundName;
    public Double holdingAmount;
    public Double holdingProfit;
    public Double holdingReturnRate;
    public Double holdingCost;
    public Double yesterdayProfit;
    public Double todayProfit;
    public Double holdingShares;
    public Double costNav;
    public String screenshotDate;
    public Double confidence;
    public List<String> rawTexts = new ArrayList<>();
    public List<PortfolioHoldingCandidate> candidates = new ArrayList<>();

    public static PortfolioHoldingImportRow fromJson(JSONObject json) {
        PortfolioHoldingImportRow row = new PortfolioHoldingImportRow();
        row.rowNo = json.optInt("rowNo");
        row.fundCode = Fund.optionalString(json, "fundCode");
        row.fundName = Fund.optionalString(json, "fundName");
        row.holdingAmount = toDouble(json, "holdingAmount");
        row.holdingProfit = toDouble(json, "holdingProfit");
        row.holdingReturnRate = toDouble(json, "holdingReturnRate");
        row.holdingCost = toDouble(json, "holdingCost");
        row.yesterdayProfit = toDouble(json, "yesterdayProfit");
        row.todayProfit = toDouble(json, "todayProfit");
        row.holdingShares = toDouble(json, "holdingShares");
        row.costNav = toDouble(json, "costNav");
        row.screenshotDate = Fund.optionalString(json, "screenshotDate");
        row.confidence = toDouble(json, "confidence");
        JSONArray rawTexts = json.optJSONArray("rawTexts");
        if (rawTexts != null) {
            for (int i = 0; i < rawTexts.length(); i++) {
                row.rawTexts.add(rawTexts.optString(i));
            }
        }
        JSONArray candidates = json.optJSONArray("candidates");
        if (candidates != null) {
            for (int i = 0; i < candidates.length(); i++) {
                row.candidates.add(PortfolioHoldingCandidate.fromJson(candidates.optJSONObject(i)));
            }
        }
        return row;
    }

    private static Double toDouble(JSONObject json, String key) {
        return json.isNull(key) || !json.has(key) ? null : json.optDouble(key);
    }
}
