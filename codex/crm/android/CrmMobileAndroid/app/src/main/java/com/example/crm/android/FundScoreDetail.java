package com.example.crm.android;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class FundScoreDetail implements Serializable {
    public FundScoreSummary summary;
    public List<FundScoreComponent> components = new ArrayList<>();
    public String disclaimer;

    public static FundScoreDetail fromJson(JSONObject json) {
        FundScoreDetail value = new FundScoreDetail();
        JSONObject summary = json.optJSONObject("summary");
        if (summary != null) value.summary = FundScoreSummary.fromJson(summary);
        JSONArray components = json.optJSONArray("components");
        if (components != null) {
            for (int index = 0; index < components.length(); index++) {
                JSONObject component = components.optJSONObject(index);
                if (component != null) value.components.add(FundScoreComponent.fromJson(component));
            }
        }
        value.disclaimer = Fund.optionalString(json, "disclaimer");
        return value;
    }
}
