package com.example.crm.android;

import org.json.JSONObject;

import java.io.Serializable;

public class FundPerformance implements Serializable {
    public String navDate;
    public String weeklyReturnRate;
    public String monthlyReturnRate;
    public String threeMonthReturnRate;
    public String sixMonthReturnRate;
    public String oneYearReturnRate;
    public String twoYearReturnRate;
    public String threeYearReturnRate;
    public String yearToDateReturnRate;
    public String sinceInceptionReturnRate;
    public String customStartDate;
    public String customEndDate;
    public String customReturnRate;
    public String originalFeeRate;
    public String discountedFeeRate;
    public String cashManagementFeeRate;

    public static FundPerformance fromJson(JSONObject json) {
        FundPerformance performance = new FundPerformance();
        performance.navDate = Fund.optionalString(json, "navDate");
        performance.weeklyReturnRate = Fund.optionalString(json, "weeklyReturnRate");
        performance.monthlyReturnRate = Fund.optionalString(json, "monthlyReturnRate");
        performance.threeMonthReturnRate = Fund.optionalString(json, "threeMonthReturnRate");
        performance.sixMonthReturnRate = Fund.optionalString(json, "sixMonthReturnRate");
        performance.oneYearReturnRate = Fund.optionalString(json, "oneYearReturnRate");
        performance.twoYearReturnRate = Fund.optionalString(json, "twoYearReturnRate");
        performance.threeYearReturnRate = Fund.optionalString(json, "threeYearReturnRate");
        performance.yearToDateReturnRate = Fund.optionalString(json, "yearToDateReturnRate");
        performance.sinceInceptionReturnRate = Fund.optionalString(json, "sinceInceptionReturnRate");
        performance.customStartDate = Fund.optionalString(json, "customStartDate");
        performance.customEndDate = Fund.optionalString(json, "customEndDate");
        performance.customReturnRate = Fund.optionalString(json, "customReturnRate");
        performance.originalFeeRate = Fund.optionalString(json, "originalFeeRate");
        performance.discountedFeeRate = Fund.optionalString(json, "discountedFeeRate");
        performance.cashManagementFeeRate = Fund.optionalString(json, "cashManagementFeeRate");
        return performance;
    }
}
