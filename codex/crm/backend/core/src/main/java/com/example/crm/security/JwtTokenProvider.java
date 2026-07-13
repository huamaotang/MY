package com.example.crm.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class JwtTokenProvider {
    private static final String HMAC = "HmacSHA256";

    @Value("${crm.jwt.secret}")
    private String secret;

    @Value("${crm.jwt.expire-seconds}")
    private long expireSeconds;

    public String createToken(Long userId, String username, List<String> permissions) {
        long now = System.currentTimeMillis() / 1000;
        long exp = now + expireSeconds;
        String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = base64Url("{\"sub\":\"" + escape(username) + "\",\"uid\":" + userId
                + ",\"iat\":" + now + ",\"exp\":" + exp + ",\"permissions\":" + toJsonArray(permissions) + "}");
        String signature = sign(header + "." + payload);
        return header + "." + payload + "." + signature;
    }

    public String getUsername(String token) {
        if (!validate(token)) {
            return null;
        }
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        return readString(payload, "sub");
    }

    public Long getUserId(String token) {
        if (!validate(token)) {
            return null;
        }
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        String value = readNumber(payload, "uid");
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public List<String> getPermissions(String token) {
        if (!validate(token)) {
            return Collections.emptyList();
        }
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        return readStringArray(payload, "permissions");
    }

    public boolean validate(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return false;
            }
            if (!sign(parts[0] + "." + parts[1]).equals(parts[2])) {
                return false;
            }
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            long exp = Long.parseLong(readNumber(payload, "exp"));
            return exp > System.currentTimeMillis() / 1000;
        } catch (Exception ex) {
            return false;
        }
    }

    private String sign(String content) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("JWT 签名失败", ex);
        }
    }

    private String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String toJsonArray(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        return values.stream()
                .map(value -> "\"" + escape(value) + "\"")
                .collect(Collectors.joining(",", "[", "]"));
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String readString(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start < 0) {
            return null;
        }
        start += pattern.length();
        int end = json.indexOf("\"", start);
        return end < 0 ? null : json.substring(start, end);
    }

    private String readNumber(String json, String key) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start < 0) {
            return "0";
        }
        start += pattern.length();
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        return json.substring(start, end);
    }

    private List<String> readStringArray(String json, String key) {
        String pattern = "\"" + key + "\":[";
        int start = json.indexOf(pattern);
        if (start < 0) {
            return Collections.emptyList();
        }
        start += pattern.length();
        int end = json.indexOf("]", start);
        if (end < 0) {
            return Collections.emptyList();
        }
        String content = json.substring(start, end).trim();
        if (content.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<>();
        for (String item : content.split(",")) {
            String value = item.trim();
            if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                values.add(value.substring(1, value.length() - 1)
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\"));
            }
        }
        return values;
    }
}
