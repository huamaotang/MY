package com.example.crm.dto;

import java.util.List;

public class LoginResponse {
    private String token;
    private String username;
    private List<String> permissions;

    public LoginResponse(String token, String username, List<String> permissions) {
        this.token = token;
        this.username = username;
        this.permissions = permissions;
    }

    public String getToken() {
        return token;
    }

    public String getUsername() {
        return username;
    }

    public List<String> getPermissions() {
        return permissions;
    }
}
