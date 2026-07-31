package com.example.crm.android;

import org.json.JSONObject;

import java.io.Serializable;

public class FundScoreComponent implements Serializable {
    public String factorKey;
    public String label;
    public String rawValue;
    public String normalizedScore;
    public int weight;
    public String effectiveWeight;
    public String contribution;

    public static FundScoreComponent fromJson(JSONObject json) {
        FundScoreComponent value = new FundScoreComponent();
        value.factorKey = Fund.optionalString(json, "factorKey");
        value.label = Fund.optionalString(json, "label");
        value.rawValue = Fund.optionalString(json, "rawValue");
        value.normalizedScore = Fund.optionalString(json, "normalizedScore");
        value.weight = json.optInt("weight");
        value.effectiveWeight = Fund.optionalString(json, "effectiveWeight");
        value.contribution = Fund.optionalString(json, "contribution");
        return value;
    }
}
