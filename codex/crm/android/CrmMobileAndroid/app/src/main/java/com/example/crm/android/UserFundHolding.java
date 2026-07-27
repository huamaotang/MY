package com.example.crm.android;

import org.json.JSONObject;

import java.io.Serializable;

public class UserFundHolding implements Serializable {
    public int id;
    public String ownerUsername;
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
    public String valuationDate;
    public String holdingReportDate;
    public Double estimatedChangeRate;
    public Double estimatedDailyProfit;
    public Double estimatedHoldingAmount;
    public Double estimatedUnitNav;
    public Double estimatedCumulativeChangeRate;
    public Double estimatedCumulativeProfit;
    public Double valuationCoverageRate;
    public String valuationUpdatedAt;
    public String screenshotDate;
    public Integer latestImportId;
    public String latestImportAt;
    public String createdAt;
    public String updatedAt;

    public static UserFundHolding fromJson(JSONObject json) {
        UserFundHolding holding = new UserFundHolding();
        holding.id = json.optInt("id");
        holding.ownerUsername = Fund.optionalString(json, "ownerUsername");
        holding.fundCode = Fund.optionalString(json, "fundCode");
        holding.fundName = Fund.optionalString(json, "fundName");
        holding.holdingAmount = toDouble(json, "holdingAmount");
        holding.holdingProfit = toDouble(json, "holdingProfit");
        holding.holdingReturnRate = toDouble(json, "holdingReturnRate");
        holding.holdingCost = toDouble(json, "holdingCost");
        holding.yesterdayProfit = toDouble(json, "yesterdayProfit");
        holding.todayProfit = toDouble(json, "todayProfit");
        holding.holdingShares = toDouble(json, "holdingShares");
        holding.costNav = toDouble(json, "costNav");
        holding.valuationDate = Fund.optionalString(json, "valuationDate");
        holding.holdingReportDate = Fund.optionalString(json, "holdingReportDate");
        holding.estimatedChangeRate = toDouble(json, "estimatedChangeRate");
        holding.estimatedDailyProfit = toDouble(json, "estimatedDailyProfit");
        holding.estimatedHoldingAmount = toDouble(json, "estimatedHoldingAmount");
        holding.estimatedUnitNav = toDouble(json, "estimatedUnitNav");
        holding.estimatedCumulativeChangeRate = toDouble(json, "estimatedCumulativeChangeRate");
        holding.estimatedCumulativeProfit = toDouble(json, "estimatedCumulativeProfit");
        holding.valuationCoverageRate = toDouble(json, "valuationCoverageRate");
        holding.valuationUpdatedAt = Fund.optionalString(json, "valuationUpdatedAt");
        holding.screenshotDate = Fund.optionalString(json, "screenshotDate");
        holding.latestImportId = json.isNull("latestImportId") || !json.has("latestImportId") ? null : json.optInt("latestImportId");
        holding.latestImportAt = Fund.optionalString(json, "latestImportAt");
        holding.createdAt = Fund.optionalString(json, "createdAt");
        holding.updatedAt = Fund.optionalString(json, "updatedAt");
        return holding;
    }

    private static Double toDouble(JSONObject json, String key) {
        return json.isNull(key) || !json.has(key) ? null : json.optDouble(key);
    }
}
