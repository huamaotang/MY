package com.example.crm.android;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

public class PortfolioHoldingConfirmRequest {
    public String screenshotDate;
    public List<PortfolioHoldingConfirmItemRequest> items;
    public List<PortfolioTradeMappingRequest> tradeMappings;

    public JSONObject toJson() throws Exception {
        JSONObject json = new JSONObject();
        json.put("screenshotDate", screenshotDate);
        if (items != null) {
            JSONArray array = new JSONArray();
            for (PortfolioHoldingConfirmItemRequest item : items) {
                array.put(item.toJson());
            }
            json.put("items", array);
        }
        if (tradeMappings != null) {
            JSONArray array = new JSONArray();
            for (PortfolioTradeMappingRequest mapping : tradeMappings) {
                array.put(mapping.toJson());
            }
            json.put("tradeMappings", array);
        }
        return json;
    }
}
