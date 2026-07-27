package com.example.crm.android;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

public class PortfolioHoldingConfirmItemRequest {
    public int rowNo;
    public String fundCode;
    public String fundName;
    public Double holdingAmount;
    public Double holdingProfit;
    public Double holdingReturnRate;
    public Double holdingCost;
    public Double yesterdayProfit;
    public Double todayProfit;
    public Double holdingShares;
    public Double costNav;
    public String screenshotDate;
    public Double confidence;
    public List<String> rawTexts;

    public JSONObject toJson() throws Exception {
        JSONObject json = new JSONObject();
        json.put("rowNo", rowNo);
        json.put("fundCode", fundCode);
        json.put("fundName", fundName);
        json.put("holdingAmount", holdingAmount);
        json.put("holdingProfit", holdingProfit);
        json.put("holdingReturnRate", holdingReturnRate);
        json.put("holdingCost", holdingCost);
        json.put("yesterdayProfit", yesterdayProfit);
        json.put("todayProfit", todayProfit);
        json.put("holdingShares", holdingShares);
        json.put("costNav", costNav);
        json.put("screenshotDate", screenshotDate);
        json.put("confidence", confidence);
        JSONArray array = new JSONArray();
        if (rawTexts != null) {
            for (String text : rawTexts) {
                array.put(text);
            }
        }
        json.put("rawTexts", array);
        return json;
    }
}
