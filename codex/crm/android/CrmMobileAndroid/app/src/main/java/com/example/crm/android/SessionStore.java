package com.example.crm.android;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionStore {
    public static final String DEFAULT_BASE_URL = "http://192.168.1.100:8780/api";

    private static final String PREFS = "crm_mobile";
    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_USERNAME = "username";

    private final SharedPreferences preferences;
    private final ApiClient apiClient;

    public SessionStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        apiClient = new ApiClient(getBaseUrl(), getToken());
    }

    public ApiClient apiClient() {
        return apiClient;
    }

    public boolean isAuthenticated() {
        String token = getToken();
        return token != null && !token.isEmpty();
    }

    public String getBaseUrl() {
        return preferences.getString(KEY_BASE_URL, DEFAULT_BASE_URL);
    }

    public String getToken() {
        return preferences.getString(KEY_TOKEN, null);
    }

    public String getUsername() {
        return preferences.getString(KEY_USERNAME, "");
    }

    public void saveLogin(String baseUrl, LoginResult result) {
        preferences.edit()
                .putString(KEY_BASE_URL, baseUrl)
                .putString(KEY_TOKEN, result.token)
                .putString(KEY_USERNAME, result.username)
                .apply();
        apiClient.setBaseUrl(baseUrl);
        apiClient.setToken(result.token);
    }

    public void logout() {
        preferences.edit()
                .remove(KEY_TOKEN)
                .remove(KEY_USERNAME)
                .apply();
        apiClient.setToken(null);
    }
}
