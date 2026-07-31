package com.example.crm.android;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class PortfolioTradeAdjustment {
    public String groupKey;
    public String fundCode;
    public String fundName;
    public Double buyAmount;
    public Double sellAmount;
    public Double netAmount;
    public Double currentHoldingAmount;
    public Double projectedHoldingAmount;
    public int transactionCount;
    public int skippedCount;
    public boolean applicable;
    public List<String> warnings = new ArrayList<>();
    public List<PortfolioHoldingCandidate> candidates = new ArrayList<>();

    public static PortfolioTradeAdjustment fromJson(JSONObject json) {
        PortfolioTradeAdjustment adjustment = new PortfolioTradeAdjustment();
        if (json == null) {
            return adjustment;
        }
        adjustment.groupKey = Fund.optionalString(json, "groupKey");
        adjustment.fundCode = Fund.optionalString(json, "fundCode");
        adjustment.fundName = Fund.optionalString(json, "fundName");
        adjustment.buyAmount = toDouble(json, "buyAmount");
        adjustment.sellAmount = toDouble(json, "sellAmount");
        adjustment.netAmount = toDouble(json, "netAmount");
        adjustment.currentHoldingAmount = toDouble(json, "currentHoldingAmount");
        adjustment.projectedHoldingAmount = toDouble(json, "projectedHoldingAmount");
        adjustment.transactionCount = json.optInt("transactionCount");
        adjustment.skippedCount = json.optInt("skippedCount");
        adjustment.applicable = json.optBoolean("applicable");
        JSONArray warnings = json.optJSONArray("warnings");
        if (warnings != null) {
            for (int i = 0; i < warnings.length(); i++) {
                adjustment.warnings.add(warnings.optString(i));
            }
        }
        JSONArray candidates = json.optJSONArray("candidates");
        if (candidates != null) {
            for (int i = 0; i < candidates.length(); i++) {
                adjustment.candidates.add(PortfolioHoldingCandidate.fromJson(candidates.optJSONObject(i)));
            }
        }
        return adjustment;
    }

    private static Double toDouble(JSONObject json, String key) {
        return json.isNull(key) || !json.has(key) ? null : json.optDouble(key);
    }
}
