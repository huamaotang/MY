package com.example.crm.android;

import org.json.JSONObject;

import java.io.Serializable;

public class FundNav implements Serializable {
    public String navDate;
    public String unitNav;
    public String accumulatedNav;
    public String dailyGrowthRate;

    public static FundNav fromJson(JSONObject json) {
        FundNav nav = new FundNav();
        nav.navDate = Fund.optionalString(json, "navDate");
        nav.unitNav = Fund.optionalString(json, "unitNav");
        nav.accumulatedNav = Fund.optionalString(json, "accumulatedNav");
        nav.dailyGrowthRate = Fund.optionalString(json, "dailyGrowthRate");
        return nav;
    }
}
