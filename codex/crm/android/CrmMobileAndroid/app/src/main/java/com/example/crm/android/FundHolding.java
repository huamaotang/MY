package com.example.crm.android;

import org.json.JSONObject;

import java.io.Serializable;

public class FundHolding implements Serializable {
    public String fundCode;
    public String reportPeriod;
    public String reportDate;
    public Integer rankNo;
    public String stockCode;
    public String stockName;
    public String latestPrice;
    public String changeRate;
    public String netValueRatio;
    public String holdingShares10k;
    public String holdingMarketValue10k;

    public static FundHolding fromJson(JSONObject json) {
        FundHolding holding = new FundHolding();
        holding.fundCode = Fund.optionalString(json, "fundCode");
        holding.reportPeriod = Fund.optionalString(json, "reportPeriod");
        holding.reportDate = Fund.optionalString(json, "reportDate");
        holding.rankNo = json.isNull("rankNo") || !json.has("rankNo") ? null : json.optInt("rankNo");
        holding.stockCode = Fund.optionalString(json, "stockCode");
        holding.stockName = Fund.optionalString(json, "stockName");
        holding.latestPrice = Fund.optionalString(json, "latestPrice");
        holding.changeRate = Fund.optionalString(json, "changeRate");
        holding.netValueRatio = Fund.optionalString(json, "netValueRatio");
        holding.holdingShares10k = Fund.optionalString(json, "holdingShares10k");
        holding.holdingMarketValue10k = Fund.optionalString(json, "holdingMarketValue10k");
        return holding;
    }
}
