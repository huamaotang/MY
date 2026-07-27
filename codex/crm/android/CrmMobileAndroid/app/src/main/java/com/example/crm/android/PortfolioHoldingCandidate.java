package com.example.crm.android;

import org.json.JSONObject;

public class PortfolioHoldingCandidate {
    public String fundCode;
    public String fundName;
    public Integer score;

    public static PortfolioHoldingCandidate fromJson(JSONObject json) {
        PortfolioHoldingCandidate candidate = new PortfolioHoldingCandidate();
        candidate.fundCode = Fund.optionalString(json, "fundCode");
        candidate.fundName = Fund.optionalString(json, "fundName");
        candidate.score = json.isNull("score") || !json.has("score") ? null : json.optInt("score");
        return candidate;
    }
}
