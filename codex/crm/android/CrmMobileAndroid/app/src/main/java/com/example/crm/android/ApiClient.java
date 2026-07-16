package com.example.crm.android;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class ApiClient {
    private static final int TIMEOUT_MS = 15000;

    private String baseUrl;
    private String token;

    public ApiClient(String baseUrl, String token) {
        setBaseUrl(baseUrl);
        this.token = token;
    }

    public void setBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            this.baseUrl = "";
            return;
        }
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (!trimmed.contains("://")) {
            trimmed = "http://" + trimmed;
        }
        this.baseUrl = trimmed;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public LoginResult login(String username, String password) throws ApiException {
        try {
            JSONObject body = new JSONObject();
            body.put("username", username);
            body.put("password", password);
            JSONObject data = request("POST", "/auth/login", body);
            LoginResult result = new LoginResult();
            result.token = data.optString("token");
            result.username = data.optString("username");
            return result;
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException("登录失败", ex);
        }
    }

    public PageResult<Customer> listCustomers(int current, int size, String keyword) throws ApiException {
        try {
            StringBuilder path = new StringBuilder("/customers?current=")
                    .append(current)
                    .append("&size=")
                    .append(size);
            if (keyword != null && !keyword.trim().isEmpty()) {
                path.append("&keyword=").append(URLEncoder.encode(keyword.trim(), "UTF-8"));
            }
            JSONObject data = request("GET", path.toString(), null);
            PageResult<Customer> page = new PageResult<>();
            page.total = data.optInt("total");
            page.size = data.optInt("size");
            page.current = data.optInt("current");
            JSONArray records = data.optJSONArray("records");
            if (records != null) {
                for (int i = 0; i < records.length(); i++) {
                    page.records.add(Customer.fromJson(records.getJSONObject(i)));
                }
            }
            return page;
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException("加载客户失败", ex);
        }
    }

    public Customer customerDetail(int id) throws ApiException {
        try {
            return Customer.fromJson(request("GET", "/customers/" + id, null));
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException("加载客户详情失败", ex);
        }
    }

    private JSONObject request(String method, String path, JSONObject body) throws Exception {
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new ApiException("服务器地址无效");
        }

        URL url = new URL(baseUrl + path);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("X-Client-Source", "android");
        connection.setRequestProperty("User-Agent", "CrmMobile/Android");
        if (token != null && !token.isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + token);
        }

        if (body != null) {
            connection.setDoOutput(true);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8));
            writer.write(body.toString());
            writer.flush();
            writer.close();
        }

        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
        String text = readAll(stream);
        if (text.isEmpty()) {
            throw new ApiException("服务器未返回数据");
        }

        JSONObject response = new JSONObject(text);
        int code = response.optInt("code", -1);
        String message = response.optString("message", "请求失败 (" + status + ")");
        if (status < 200 || status >= 300 || code != 0) {
            throw new ApiException(message);
        }
        JSONObject data = response.optJSONObject("data");
        if (data == null) {
            throw new ApiException("服务器未返回数据");
        }
        return data;
    }

    private String readAll(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        reader.close();
        return builder.toString();
    }
}
