package com.example.crm.android;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class PortfolioOverview {
    public PortfolioAccountSummary total;
    public List<PortfolioAccountSummary> accounts = new ArrayList<>();

    public static PortfolioOverview fromJson(JSONObject json) {
        PortfolioOverview overview = new PortfolioOverview();
        JSONObject totalJson = json.optJSONObject("total");
        if (totalJson != null) {
            overview.total = PortfolioAccountSummary.fromJson(totalJson);
        }
        JSONArray values = json.optJSONArray("accounts");
        if (values != null) {
            for (int i = 0; i < values.length(); i++) {
                JSONObject account = values.optJSONObject(i);
                if (account != null) {
                    overview.accounts.add(PortfolioAccountSummary.fromJson(account));
                }
            }
        }
        return overview;
    }
}
