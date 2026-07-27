package com.example.crm.android;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class FundDetail implements Serializable {
    public Fund fund;
    public FundNav latestNav;
    public FundPerformance latestPerformance;
    public FundDailyValuation latestValuation;
    public List<FundHolding> latestHoldings = new ArrayList<>();
    public List<FundFeature> features = new ArrayList<>();
    public List<FundRating> ratings = new ArrayList<>();

    public static FundDetail fromJson(JSONObject json) {
        FundDetail detail = new FundDetail();
        JSONObject fundJson = json.optJSONObject("fund");
        if (fundJson != null) {
            detail.fund = Fund.fromJson(fundJson);
        }
        JSONObject navJson = json.optJSONObject("latestNav");
        if (navJson != null) {
            detail.latestNav = FundNav.fromJson(navJson);
        }
        JSONObject performanceJson = json.optJSONObject("latestPerformance");
        if (performanceJson != null) {
            detail.latestPerformance = FundPerformance.fromJson(performanceJson);
        }
        JSONObject valuationJson = json.optJSONObject("latestValuation");
        if (valuationJson != null) {
            detail.latestValuation = FundDailyValuation.fromJson(valuationJson);
        }
        JSONArray holdings = json.optJSONArray("latestHoldings");
        if (holdings != null) {
            for (int i = 0; i < holdings.length(); i++) {
                detail.latestHoldings.add(FundHolding.fromJson(holdings.optJSONObject(i)));
            }
        }
        JSONArray features = json.optJSONArray("features");
        if (features != null) {
            for (int i = 0; i < features.length(); i++) {
                detail.features.add(FundFeature.fromJson(features.optJSONObject(i)));
            }
        }
        JSONArray ratings = json.optJSONArray("ratings");
        if (ratings != null) {
            for (int i = 0; i < ratings.length(); i++) {
                detail.ratings.add(FundRating.fromJson(ratings.optJSONObject(i)));
            }
        }
        return detail;
    }
}
