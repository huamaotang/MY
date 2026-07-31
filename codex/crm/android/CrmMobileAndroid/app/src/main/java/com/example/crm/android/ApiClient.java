package com.example.crm.android;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

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

    public PageResult<FinanceNews> listFinanceNews(int current, int size, int categoryTag) throws ApiException {
        try {
            String path="/news?current="+current+"&size="+size+(categoryTag < 0 ? "" : "&categoryTag="+categoryTag);
            JSONObject data=request("GET", path, null); PageResult<FinanceNews> page=new PageResult<>();
            page.total=data.optInt("total"); page.current=data.optInt("current"); page.size=data.optInt("size"); JSONArray records=data.optJSONArray("records");
            if(records!=null) for(int i=0;i<records.length();i++) page.records.add(FinanceNews.fromJson(records.getJSONObject(i))); return page;
        } catch(Exception ex) { throw new ApiException("加载资讯失败", ex); }
    }

    public PageResult<StockQuote> listStocks(int current, int size, String keyword) throws ApiException {
        try {
            String path="/stocks?current="+current+"&size="+size+"&sortField=changeRate&sortOrder=descend";
            if(keyword!=null&&!keyword.trim().isEmpty()) path += "&keyword="+URLEncoder.encode(keyword.trim(), "UTF-8");
            JSONObject data=request("GET", path, null); PageResult<StockQuote> page=new PageResult<>();
            page.total=data.optInt("total"); page.current=data.optInt("current"); page.size=data.optInt("size");
            JSONArray records=data.optJSONArray("records");
            if(records!=null) for(int i=0;i<records.length();i++) page.records.add(StockQuote.fromJson(records.getJSONObject(i)));
            return page;
        } catch(Exception ex) { throw new ApiException("加载股票行情失败", ex); }
    }

    public PageResult<StockQuote> stockHistory(String stockCode, int current, int size) throws ApiException {
        try {
            JSONObject data=request("GET", "/stocks/"+stockCode+"/history?current="+current+"&size="+size, null);
            PageResult<StockQuote> page=new PageResult<>(); page.total=data.optInt("total"); page.current=data.optInt("current"); page.size=data.optInt("size");
            JSONArray records=data.optJSONArray("records");
            if(records!=null) for(int i=0;i<records.length();i++) page.records.add(StockQuote.fromJson(records.getJSONObject(i)));
            return page;
        } catch(Exception ex) { throw new ApiException("加载股票历史失败", ex); }
    }

    public PageResult<Fund> listFunds(int current, int size, String keyword) throws ApiException {
        return listFunds(current, size, keyword, null, null, null, null);
    }

    public PageResult<Fund> listFunds(int current, int size, String keyword, String fundType,
                                      Boolean canBuy, String sortField, String sortOrder) throws ApiException {
        try {
            StringBuilder path = new StringBuilder("/funds?current=")
                    .append(current)
                    .append("&size=")
                    .append(size);
            if (keyword != null && !keyword.trim().isEmpty()) {
                path.append("&keyword=").append(URLEncoder.encode(keyword.trim(), "UTF-8"));
            }
            appendQuery(path, "fundType", fundType);
            if (canBuy != null) path.append("&canBuy=").append(canBuy);
            appendQuery(path, "sortField", sortField);
            appendQuery(path, "sortOrder", sortOrder);
            JSONObject data = request("GET", path.toString(), null);
            PageResult<Fund> page = new PageResult<>();
            page.total = data.optInt("total");
            page.size = data.optInt("size");
            page.current = data.optInt("current");
            JSONArray records = data.optJSONArray("records");
            if (records != null) {
                for (int i = 0; i < records.length(); i++) {
                    page.records.add(Fund.fromJson(records.getJSONObject(i)));
                }
            }
            return page;
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException("加载产品失败", ex);
        }
    }

    private void appendQuery(StringBuilder path, String name, String value) throws Exception {
        if (value != null && !value.trim().isEmpty()) {
            path.append("&").append(name).append("=")
                    .append(URLEncoder.encode(value.trim(), "UTF-8"));
        }
    }

    public FundDetail fundDetail(String fundCode) throws ApiException {
        try {
            return FundDetail.fromJson(request("GET", "/funds/" + URLEncoder.encode(fundCode, "UTF-8"), null));
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException("加载产品详情失败", ex);
        }
    }

    public PageResult<FundNav> listFundNavs(String fundCode, int current, int size) throws ApiException {
        try {
            String path = "/funds/" + URLEncoder.encode(fundCode, "UTF-8")
                    + "/navs?current=" + current
                    + "&size=" + size;
            JSONObject data = request("GET", path, null);
            PageResult<FundNav> page = new PageResult<>();
            page.total = data.optInt("total");
            page.size = data.optInt("size");
            page.current = data.optInt("current");
            JSONArray records = data.optJSONArray("records");
            if (records != null) {
                for (int i = 0; i < records.length(); i++) {
                    page.records.add(FundNav.fromJson(records.getJSONObject(i)));
                }
            }
            return page;
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException("加载净值走势失败", ex);
        }
    }

    public PageResult<UserFundHolding> listPortfolioHoldings(int current, int size, String keyword) throws ApiException {
        return listPortfolioHoldings(current, size, keyword, "raw", "holdingAmount", "desc");
    }

    public PageResult<UserFundHolding> listPortfolioHoldings(int current, int size, String keyword,
                                                              String scope, String sortField,
                                                              String sortOrder) throws ApiException {
        try {
            StringBuilder path = new StringBuilder("/portfolio/holdings?current=")
                    .append(current)
                    .append("&size=")
                    .append(size)
                    .append("&scope=").append(scope)
                    .append("&sortField=").append(sortField)
                    .append("&sortOrder=").append(sortOrder);
            if (keyword != null && !keyword.trim().isEmpty()) {
                path.append("&keyword=").append(URLEncoder.encode(keyword.trim(), "UTF-8"));
            }
            JSONObject data = request("GET", path.toString(), null);
            PageResult<UserFundHolding> page = new PageResult<>();
            page.total = data.optInt("total");
            page.size = data.optInt("size");
            page.current = data.optInt("current");
            JSONArray records = data.optJSONArray("records");
            if (records != null) {
                for (int i = 0; i < records.length(); i++) {
                    page.records.add(UserFundHolding.fromJson(records.getJSONObject(i)));
                }
            }
            return page;
        } catch (Exception ex) {
            throw new ApiException("加载持仓失败", ex);
        }
    }

    public PortfolioOverview portfolioOverview() throws ApiException {
        try {
            return PortfolioOverview.fromJson(request("GET", "/portfolio/overview", null));
        } catch (Exception ex) {
            throw new ApiException("加载账户汇总失败", ex);
        }
    }

    public PageResult<PortfolioHoldingBatch> listPortfolioImports(int current, int size) throws ApiException {
        try {
            JSONObject data = request("GET", "/portfolio/imports?current=" + current + "&size=" + size, null);
            PageResult<PortfolioHoldingBatch> page = new PageResult<>();
            page.total = data.optInt("total");
            page.size = data.optInt("size");
            page.current = data.optInt("current");
            JSONArray records = data.optJSONArray("records");
            if (records != null) {
                for (int i = 0; i < records.length(); i++) {
                    page.records.add(PortfolioHoldingBatch.fromJson(records.getJSONObject(i)));
                }
            }
            return page;
        } catch (Exception ex) {
            throw new ApiException("加载导入历史失败", ex);
        }
    }

    public PortfolioHoldingImportPreview previewPortfolioHoldings(List<byte[]> images) throws ApiException {
        return previewPortfolioHoldings(images, "alipay", "holding");
    }

    public PortfolioHoldingImportPreview previewPortfolioHoldings(List<byte[]> images,
                                                                   String sourceLabel) throws ApiException {
        return previewPortfolioHoldings(images, sourceLabel, "holding");
    }

    public PortfolioHoldingImportPreview previewPortfolioHoldings(List<byte[]> images,
                                                                   String sourceLabel,
                                                                   String importType) throws ApiException {
        try {
            String path = "/portfolio/imports/ocr?sourceLabel="
                    + URLEncoder.encode(sourceLabel, "UTF-8")
                    + "&importType=" + URLEncoder.encode(importType, "UTF-8");
            JSONObject data = requestMultipart(path, images);
            return PortfolioHoldingImportPreview.fromJson(data);
        } catch (Exception ex) {
            throw new ApiException("识别导入截图失败", ex);
        }
    }

    public PortfolioHoldingImportPreview portfolioHoldingImport(int importId) throws ApiException {
        try {
            return PortfolioHoldingImportPreview.fromJson(request("GET", "/portfolio/imports/" + importId, null));
        } catch (Exception ex) {
            throw new ApiException("加载导入详情失败", ex);
        }
    }

    public PortfolioHoldingConfirmResponse confirmPortfolioHoldingImport(
            int importId, PortfolioHoldingConfirmRequest requestBody) throws ApiException {
        try {
            JSONObject data = request(
                    "POST", "/portfolio/imports/" + importId + "/confirm", requestBody.toJson());
            return PortfolioHoldingConfirmResponse.fromJson(data);
        } catch (Exception ex) {
            throw new ApiException("确认入库失败", ex);
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

    private void requestNoData(String method, String path, JSONObject body) throws Exception {
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
        connection.setDoOutput(true);
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8));
        writer.write(body.toString());
        writer.flush();
        writer.close();

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
    }

    private JSONObject requestMultipart(String path, List<byte[]> images) throws Exception {
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new ApiException("服务器地址无效");
        }

        String boundary = "Boundary-" + UUID.randomUUID();
        URL url = new URL(baseUrl + path);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setDoOutput(true);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        connection.setRequestProperty("X-Client-Source", "android");
        connection.setRequestProperty("User-Agent", "CrmMobile/Android");
        if (token != null && !token.isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + token);
        }

        OutputStream outputStream = connection.getOutputStream();
        for (int i = 0; i < images.size(); i++) {
            byte[] image = images.get(i);
            outputStream.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            outputStream.write(("Content-Disposition: form-data; name=\"images\"; filename=\"screenshot-" + (i + 1) + ".jpg\"\r\n").getBytes(StandardCharsets.UTF_8));
            outputStream.write(("Content-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            outputStream.write(image);
            outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        outputStream.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
        outputStream.close();

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
