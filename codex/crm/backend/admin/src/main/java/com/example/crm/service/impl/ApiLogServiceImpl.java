package com.example.crm.service.impl;

import com.example.crm.dto.ApiLogRequest;
import com.example.crm.service.IApiLogService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ApiLogServiceImpl implements IApiLogService {
    private final JdbcTemplate jdbcTemplate;

    public ApiLogServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void create(ApiLogRequest request) {
        jdbcTemplate.update(
                "INSERT INTO sys_api_log (trace_id, service_name, request_method, request_uri, query_string, "
                        + "source, user_id, username, ip, user_agent, http_status, success, error_message, "
                        + "duration_ms, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())",
                truncate(request.getTraceId(), 64),
                truncate(request.getServiceName(), 80),
                truncate(request.getRequestMethod(), 10),
                truncate(request.getRequestUri(), 300),
                truncate(request.getQueryString(), 1000),
                truncate(request.getSource(), 40),
                request.getUserId(),
                truncate(request.getUsername(), 50),
                truncate(request.getIp(), 64),
                truncate(request.getUserAgent(), 500),
                request.getHttpStatus(),
                request.getSuccess(),
                truncate(request.getErrorMessage(), 500),
                request.getDurationMs()
        );
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
