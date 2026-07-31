package com.example.crm.android;

import org.json.JSONObject;

import java.io.Serializable;

public class FundScoreSummary implements Serializable {
    public long profileId;
    public String profileName;
    public int profileVersion;
    public String validationStatus;
    public String asOfDate;
    public String totalScore;
    public String profitProbability;
    public String confidence;
    public String dataCoverage;
    public String comparisonGroup;
    public Integer categoryRank;
    public Integer categoryCount;
    public String methodologyVersion;

    public static FundScoreSummary fromJson(JSONObject json) {
        FundScoreSummary value = new FundScoreSummary();
        value.profileId = json.optLong("profileId");
        value.profileName = Fund.optionalString(json, "profileName");
        value.profileVersion = json.optInt("profileVersion");
        value.validationStatus = Fund.optionalString(json, "validationStatus");
        value.asOfDate = Fund.optionalString(json, "asOfDate");
        value.totalScore = Fund.optionalString(json, "totalScore");
        value.profitProbability = Fund.optionalString(json, "profitProbability");
        value.confidence = Fund.optionalString(json, "confidence");
        value.dataCoverage = Fund.optionalString(json, "dataCoverage");
        value.comparisonGroup = Fund.optionalString(json, "comparisonGroup");
        value.categoryRank = json.isNull("categoryRank") ? null : json.optInt("categoryRank");
        value.categoryCount = json.isNull("categoryCount") ? null : json.optInt("categoryCount");
        value.methodologyVersion = Fund.optionalString(json, "methodologyVersion");
        return value;
    }
}
