package com.example.crm.android;

import org.json.JSONObject;

import java.io.Serializable;

public class FundFeature implements Serializable {
    public String periodLabel;
    public String cutoffDate;
    public String standardDeviation;
    public String sharpeRatio;

    public static FundFeature fromJson(JSONObject json) {
        FundFeature feature = new FundFeature();
        feature.periodLabel = Fund.optionalString(json, "periodLabel");
        feature.cutoffDate = Fund.optionalString(json, "cutoffDate");
        feature.standardDeviation = Fund.optionalString(json, "standardDeviation");
        feature.sharpeRatio = Fund.optionalString(json, "sharpeRatio");
        return feature;
    }
}
