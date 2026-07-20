package com.example.crm.controller;

import com.example.crm.common.ApiResponse;
import com.example.crm.dto.ApiLogRequest;
import com.example.crm.service.IApiLogService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api-logs")
public class ApiLogController {
    private final IApiLogService apiLogService;

    @Value("${crm.access-log.ingest-token:change-this-access-log-token}")
    private String ingestToken;

    public ApiLogController(IApiLogService apiLogService) {
        this.apiLogService = apiLogService;
    }

    @PostMapping
    public ApiResponse<Void> create(@RequestHeader(value = "X-Access-Log-Token", required = false) String token,
                                    @RequestBody ApiLogRequest request) {
        if (ingestToken != null && !ingestToken.isEmpty() && !ingestToken.equals(token)) {
            return ApiResponse.fail("访问日志写入令牌无效");
        }
        apiLogService.create(request);
        return ApiResponse.ok();
    }
}
