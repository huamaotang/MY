package com.example.crm.logging;

import com.example.crm.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import javax.annotation.PostConstruct;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "crm.access-log", name = "enabled", havingValue = "true", matchIfMissing = true)
@Order(Ordered.LOWEST_PRECEDENCE - 10)
@Slf4j
public class AccessLogFilter extends OncePerRequestFilter {
    private static final int REQUEST_CACHE_LIMIT = 2048;

    private final JdbcTemplate jdbcTemplate;
    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;

    @Value("${spring.application.name:unknown}")
    private String serviceName;

    public AccessLogFilter(JdbcTemplate jdbcTemplate, JwtTokenProvider jwtTokenProvider, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.jwtTokenProvider = jwtTokenProvider;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        log.info("api access log filter enabled for service {}", serviceName);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || uri.startsWith("/actuator/")
                || uri.endsWith("/actuator/health")
                || uri.endsWith("/actuator/info")
                || uri.endsWith("/actuator/serviceregistry");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long startedAt = System.currentTimeMillis();
        Throwable error = null;
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request, REQUEST_CACHE_LIMIT);

        try {
            filterChain.doFilter(wrappedRequest, response);
        } catch (IOException | ServletException | RuntimeException ex) {
            error = ex;
            throw ex;
        } finally {
            writeAccessLog(wrappedRequest, response, startedAt, error);
        }
    }

    private void writeAccessLog(ContentCachingRequestWrapper request, HttpServletResponse response,
                                long startedAt, Throwable error) {
        try {
            String token = resolveBearerToken(request);
            Long userId = token == null ? null : jwtTokenProvider.getUserId(token);
            String username = token == null ? null : jwtTokenProvider.getUsername(token);
            if (username == null && isLoginRequest(request)) {
                username = readLoginUsername(request);
            }

            int status = response.getStatus();
            long durationMs = System.currentTimeMillis() - startedAt;
            String traceId = firstNotBlank(request.getHeader("X-Request-Id"), UUID.randomUUID().toString());
            String errorMessage = errorMessage(error, status);

            jdbcTemplate.update(
                    "INSERT INTO sys_api_log (trace_id, service_name, request_method, request_uri, query_string, "
                            + "source, user_id, username, ip, user_agent, http_status, success, error_message, "
                            + "duration_ms, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())",
                    truncate(traceId, 64),
                    truncate(serviceName, 80),
                    truncate(request.getMethod(), 10),
                    truncate(request.getRequestURI(), 300),
                    truncate(request.getQueryString(), 1000),
                    truncate(resolveSource(request), 40),
                    userId,
                    truncate(username, 50),
                    truncate(resolveIp(request), 64),
                    truncate(request.getHeader("User-Agent"), 500),
                    status,
                    error == null && status < 400 ? 1 : 0,
                    truncate(errorMessage, 500),
                    durationMs
            );
        } catch (Exception ex) {
            log.warn("write api access log failed", ex);
        }
    }

    private String resolveBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return null;
    }

    private boolean isLoginRequest(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod()) && request.getRequestURI().endsWith("/auth/login");
    }

    private String readLoginUsername(ContentCachingRequestWrapper request) {
        byte[] content = request.getContentAsByteArray();
        if (content.length == 0) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(new String(content, StandardCharsets.UTF_8));
            return root.path("username").asText(null);
        } catch (Exception ex) {
            return null;
        }
    }

    private String resolveIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (hasText(realIp)) {
            return realIp.trim();
        }
        String forwarded = request.getHeader("Forwarded");
        if (hasText(forwarded)) {
            for (String part : forwarded.split(";")) {
                String trimmed = part.trim();
                if (trimmed.toLowerCase(Locale.ROOT).startsWith("for=")) {
                    return trimmed.substring(4).replace("\"", "");
                }
            }
        }
        return request.getRemoteAddr();
    }

    private String resolveSource(HttpServletRequest request) {
        String source = request.getHeader("X-Client-Source");
        if (hasText(source)) {
            return normalizeSource(source);
        }
        String userAgent = request.getHeader("User-Agent");
        if (!hasText(userAgent)) {
            return "unknown";
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        if (ua.contains("crmmobile") || ua.contains("iphone") || ua.contains("ipad") || ua.contains("cfnetwork")) {
            return "ios";
        }
        if (ua.contains("android")) {
            return "android";
        }
        if (ua.contains("mozilla")) {
            return "web";
        }
        return "unknown";
    }

    private String normalizeSource(String source) {
        String value = source.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        if ("iphone".equals(value) || "ipad".equals(value)) {
            return "ios";
        }
        if ("browser".equals(value)) {
            return "web";
        }
        return value.replaceAll("[^a-z0-9-]", "");
    }

    private String errorMessage(Throwable error, int status) {
        if (error != null) {
            String message = error.getMessage();
            return error.getClass().getSimpleName() + (hasText(message) ? ": " + message : "");
        }
        return status >= 400 ? "HTTP " + status : null;
    }

    private String firstNotBlank(String first, String fallback) {
        return hasText(first) ? first : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty() && !"unknown".equalsIgnoreCase(value.trim());
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
