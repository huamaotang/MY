package com.example.crm.dto;

import lombok.Data;

@Data
public class ApiLogRequest {
    private String traceId;
    private String serviceName;
    private String requestMethod;
    private String requestUri;
    private String queryString;
    private String source;
    private Long userId;
    private String username;
    private String ip;
    private String userAgent;
    private Integer httpStatus;
    private Integer success;
    private String errorMessage;
    private Long durationMs;
}
