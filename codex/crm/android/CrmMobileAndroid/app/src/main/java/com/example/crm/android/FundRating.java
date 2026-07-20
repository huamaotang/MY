package com.example.crm.android;

import org.json.JSONObject;

import java.io.Serializable;

public class FundRating implements Serializable {
    public String fundCode;
    public String ratingDate;
    public Integer zhaoshangRating;
    public Integer shanghaiRating3y;
    public Integer shanghaiRating5y;
    public Integer jianRating;
    public Integer morningStarRating;

    public static FundRating fromJson(JSONObject json) {
        FundRating rating = new FundRating();
        if (json == null) {
            return rating;
        }
        rating.fundCode = Fund.optionalString(json, "fundCode");
        rating.ratingDate = Fund.optionalString(json, "ratingDate");
        rating.zhaoshangRating = optionalInt(json, "zhaoshangRating");
        rating.shanghaiRating3y = optionalInt(json, "shanghaiRating3y");
        rating.shanghaiRating5y = optionalInt(json, "shanghaiRating5y");
        rating.jianRating = optionalInt(json, "jianRating");
        rating.morningStarRating = optionalInt(json, "morningStarRating");
        return rating;
    }

    private static Integer optionalInt(JSONObject json, String key) {
        if (json == null || json.isNull(key)) {
            return null;
        }
        return json.optInt(key);
    }
}
