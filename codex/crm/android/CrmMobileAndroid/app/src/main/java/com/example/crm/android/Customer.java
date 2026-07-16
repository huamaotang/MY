package com.example.crm.android;

import org.json.JSONObject;

import java.io.Serializable;

public class Customer implements Serializable {
    public Integer id;
    public String customerName;
    public String customerType;
    public String industry;
    public String source;
    public String level;
    public String status;
    public Integer ownerUserId;
    public String phone;
    public String email;
    public String province;
    public String city;
    public String address;
    public String remark;
    public String createdAt;
    public String updatedAt;

    public static Customer fromJson(JSONObject json) {
        Customer customer = new Customer();
        customer.id = optionalInt(json, "id");
        customer.customerName = optionalString(json, "customerName");
        customer.customerType = optionalString(json, "customerType");
        customer.industry = optionalString(json, "industry");
        customer.source = optionalString(json, "source");
        customer.level = optionalString(json, "level");
        customer.status = optionalString(json, "status");
        customer.ownerUserId = optionalInt(json, "ownerUserId");
        customer.phone = optionalString(json, "phone");
        customer.email = optionalString(json, "email");
        customer.province = optionalString(json, "province");
        customer.city = optionalString(json, "city");
        customer.address = optionalString(json, "address");
        customer.remark = optionalString(json, "remark");
        customer.createdAt = optionalString(json, "createdAt");
        customer.updatedAt = optionalString(json, "updatedAt");
        return customer;
    }

    private static String optionalString(JSONObject json, String key) {
        return json.isNull(key) ? null : json.optString(key, null);
    }

    private static Integer optionalInt(JSONObject json, String key) {
        return json.isNull(key) || !json.has(key) ? null : json.optInt(key);
    }
}
