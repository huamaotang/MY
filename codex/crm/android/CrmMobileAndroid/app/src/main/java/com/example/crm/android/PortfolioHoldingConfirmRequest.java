package com.example.crm.android;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

public class PortfolioHoldingConfirmRequest {
    public String screenshotDate;
    public List<PortfolioHoldingConfirmItemRequest> items;

    public JSONObject toJson() throws Exception {
        JSONObject json = new JSONObject();
        json.put("screenshotDate", screenshotDate);
        JSONArray array = new JSONArray();
        if (items != null) {
            for (PortfolioHoldingConfirmItemRequest item : items) {
                array.put(item.toJson());
            }
        }
        json.put("items", array);
        return json;
    }
}
