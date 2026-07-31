package com.example.crm.android;

import org.json.JSONObject;

public class PortfolioTradeMappingRequest {
    public String groupKey;
    public String fundCode;

    public JSONObject toJson() throws Exception {
        JSONObject json = new JSONObject();
        json.put("groupKey", groupKey);
        json.put("fundCode", fundCode);
        return json;
    }
}
