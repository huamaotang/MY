package com.example.crm.android;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class PortfolioHoldingConfirmResponse {
    public int affectedHoldingCount;
    public int appliedTransactionCount;
    public int skippedTransactionCount;
    public List<String> warnings = new ArrayList<>();

    public static PortfolioHoldingConfirmResponse fromJson(JSONObject json) {
        PortfolioHoldingConfirmResponse response = new PortfolioHoldingConfirmResponse();
        response.affectedHoldingCount = json.optInt("affectedHoldingCount");
        response.appliedTransactionCount = json.optInt("appliedTransactionCount");
        response.skippedTransactionCount = json.optInt("skippedTransactionCount");
        JSONArray warnings = json.optJSONArray("warnings");
        if (warnings != null) {
            for (int i = 0; i < warnings.length(); i++) {
                response.warnings.add(warnings.optString(i));
            }
        }
        return response;
    }
}
