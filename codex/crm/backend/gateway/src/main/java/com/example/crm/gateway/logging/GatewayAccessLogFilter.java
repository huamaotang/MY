package com.example.crm.gateway.logging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Component
@ConditionalOnProperty(prefix = "crm.access-log", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GatewayAccessLogFilter implements GlobalFilter, Ordered {
    private static final Logger log = LoggerFactory.getLogger(GatewayAccessLogFilter.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${crm.access-log.admin-url:http://127.0.0.1:8781/api}")
    private String adminUrl;

    @Value("${crm.access-log.ingest-token:change-this-access-log-token}")
    private String ingestToken;

    public GatewayAccessLogFilter(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (shouldSkip(exchange.getRequest())) {
            return chain.filter(exchange);
        }

        long startedAt = System.currentTimeMillis();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        return chain.filter(exchange)
                .doOnError(errorRef::set)
                .doFinally(signalType -> sendAccessLog(exchange, startedAt, errorRef.get()));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private boolean shouldSkip(ServerHttpRequest request) {
        String path = request.getURI().getPath();
        return "OPTIONS".equalsIgnoreCase(request.getMethodValue())
                || path.startsWith("/actuator/")
                || path.endsWith("/actuator/health")
                || path.endsWith("/actuator/info")
                || path.endsWith("/actuator/serviceregistry")
                || path.equals("/api/api-logs");
    }

    private void sendAccessLog(ServerWebExchange exchange, long startedAt, Throwable error) {
        try {
            ServerHttpRequest request = exchange.getRequest();
            ApiLogPayload payload = new ApiLogPayload();
            payload.traceId = firstNotBlank(request.getHeaders().getFirst("X-Request-Id"), UUID.randomUUID().toString());
            payload.serviceName = resolveServiceName(exchange);
            payload.requestMethod = request.getMethodValue();
            payload.requestUri = request.getURI().getPath();
            payload.queryString = request.getURI().getRawQuery();
            payload.source = resolveSource(request);
            payload.ip = resolveIp(request);
            payload.userAgent = request.getHeaders().getFirst("User-Agent");
            payload.httpStatus = resolveStatus(exchange, error);
            payload.success = error == null && payload.httpStatus < 400 ? 1 : 0;
            payload.errorMessage = errorMessage(error, payload.httpStatus);
            payload.durationMs = System.currentTimeMillis() - startedAt;
            fillUser(request, payload);

            webClient.post()
                    .uri(normalizeAdminUrl() + "/api-logs")
                    .header("X-Access-Log-Token", ingestToken)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .doOnError(ex -> log.warn("send api access log to admin failed", ex))
                    .subscribe();
        } catch (Exception ex) {
            log.warn("build api access log failed", ex);
        }
    }

    private String resolveServiceName(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        return route == null ? "gateway" : route.getId();
    }

    private int resolveStatus(ServerWebExchange exchange, Throwable error) {
        if (error != null) {
            return HttpStatus.INTERNAL_SERVER_ERROR.value();
        }
        HttpStatus status = exchange.getResponse().getStatusCode();
        return status == null ? HttpStatus.OK.value() : status.value();
    }

    private void fillUser(ServerHttpRequest request, ApiLogPayload payload) {
        String authorization = request.getHeaders().getFirst("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return;
        }
        try {
            String token = authorization.substring(7);
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return;
            }
            String json = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            JsonNode root = objectMapper.readTree(json);
            if (root.hasNonNull("uid")) {
                payload.userId = root.path("uid").asLong();
            }
            if (root.hasNonNull("sub")) {
                payload.username = root.path("sub").asText();
            }
        } catch (Exception ex) {
            log.debug("parse jwt payload for access log failed", ex);
        }
    }

    private String resolveIp(ServerHttpRequest request) {
        String forwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeaders().getFirst("X-Real-IP");
        if (hasText(realIp)) {
            return realIp.trim();
        }
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        return "unknown";
    }

    private String resolveSource(ServerHttpRequest request) {
        String source = request.getHeaders().getFirst("X-Client-Source");
        if (hasText(source)) {
            return normalizeSource(source);
        }
        String userAgent = request.getHeaders().getFirst("User-Agent");
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

    private String normalizeAdminUrl() {
        String value = adminUrl == null ? "" : adminUrl.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static class ApiLogPayload {
        public String traceId;
        public String serviceName;
        public String requestMethod;
        public String requestUri;
        public String queryString;
        public String source;
        public Long userId;
        public String username;
        public String ip;
        public String userAgent;
        public Integer httpStatus;
        public Integer success;
        public String errorMessage;
        public Long durationMs;
    }
}
