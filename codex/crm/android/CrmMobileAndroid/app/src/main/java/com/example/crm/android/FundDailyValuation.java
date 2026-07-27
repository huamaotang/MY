package com.example.crm.android;

import org.json.JSONObject;

import java.io.Serializable;

public class FundDailyValuation implements Serializable {
    public String fundCode;
    public String valuationDate;
    public String holdingReportDate;
    public String baseNavDate;
    public String baseUnitNav;
    public String estimatedUnitNav;
    public String estimatedChangeRate;
    public String holdingWeight;
    public String quotedHoldingWeight;
    public String quoteCoverageRate;
    public Integer holdingCount;
    public Integer quotedHoldingCount;
    public String quoteUpdatedAt;

    public static FundDailyValuation fromJson(JSONObject json) {
        FundDailyValuation valuation = new FundDailyValuation();
        valuation.fundCode = Fund.optionalString(json, "fundCode");
        valuation.valuationDate = Fund.optionalString(json, "valuationDate");
        valuation.holdingReportDate = Fund.optionalString(json, "holdingReportDate");
        valuation.baseNavDate = Fund.optionalString(json, "baseNavDate");
        valuation.baseUnitNav = Fund.optionalString(json, "baseUnitNav");
        valuation.estimatedUnitNav = Fund.optionalString(json, "estimatedUnitNav");
        valuation.estimatedChangeRate = Fund.optionalString(json, "estimatedChangeRate");
        valuation.holdingWeight = Fund.optionalString(json, "holdingWeight");
        valuation.quotedHoldingWeight = Fund.optionalString(json, "quotedHoldingWeight");
        valuation.quoteCoverageRate = Fund.optionalString(json, "quoteCoverageRate");
        valuation.holdingCount = optionalInteger(json, "holdingCount");
        valuation.quotedHoldingCount = optionalInteger(json, "quotedHoldingCount");
        valuation.quoteUpdatedAt = Fund.optionalString(json, "quoteUpdatedAt");
        return valuation;
    }

    private static Integer optionalInteger(JSONObject json, String key) {
        return !json.has(key) || json.isNull(key) ? null : json.optInt(key);
    }
}
