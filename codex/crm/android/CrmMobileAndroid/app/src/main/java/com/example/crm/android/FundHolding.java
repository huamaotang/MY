package com.example.crm.android;

import org.json.JSONObject;

import java.io.Serializable;

public class FundHolding implements Serializable {
    public String reportDate;
    public Integer rankNo;
    public String stockCode;
    public String stockName;
    public String netValueRatio;
    public String holdingMarketValue10k;

    public static FundHolding fromJson(JSONObject json) {
        FundHolding holding = new FundHolding();
        holding.reportDate = Fund.optionalString(json, "reportDate");
        holding.rankNo = json.isNull("rankNo") || !json.has("rankNo") ? null : json.optInt("rankNo");
        holding.stockCode = Fund.optionalString(json, "stockCode");
        holding.stockName = Fund.optionalString(json, "stockName");
        holding.netValueRatio = Fund.optionalString(json, "netValueRatio");
        holding.holdingMarketValue10k = Fund.optionalString(json, "holdingMarketValue10k");
        return holding;
    }
}
