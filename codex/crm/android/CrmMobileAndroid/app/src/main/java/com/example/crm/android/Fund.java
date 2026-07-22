package com.example.crm.android;

import org.json.JSONObject;

import java.io.Serializable;

public class Fund implements Serializable {
    public String fundCode;
    public String fundName;
    public String inceptionDate;
    public String fundManager;
    public String fundType;
    public String managementCompany;
    public String netAssetScale;
    public String scaleDate;
    public boolean canBuy;
    public String createdAt;
    public String updatedAt;

    public static Fund fromJson(JSONObject json) {
        Fund fund = new Fund();
        fund.fundCode = optionalString(json, "fundCode");
        fund.fundName = optionalString(json, "fundName");
        fund.inceptionDate = optionalString(json, "inceptionDate");
        fund.fundManager = optionalString(json, "fundManager");
        fund.fundType = optionalString(json, "fundType");
        fund.managementCompany = optionalString(json, "managementCompany");
        fund.netAssetScale = optionalString(json, "netAssetScale");
        fund.scaleDate = optionalString(json, "scaleDate");
        fund.canBuy = json.optBoolean("canBuy", false);
        fund.createdAt = optionalString(json, "createdAt");
        fund.updatedAt = optionalString(json, "updatedAt");
        return fund;
    }

    static String optionalString(JSONObject json, String key) {
        return json.isNull(key) ? null : json.optString(key, null);
    }
}
