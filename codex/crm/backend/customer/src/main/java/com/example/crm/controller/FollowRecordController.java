package com.example.crm.controller;

import com.example.crm.common.ApiResponse;
import com.example.crm.entity.CrmFollowRecord;
import com.example.crm.service.IFollowRecordService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/follow-records")
public class FollowRecordController {
    private final IFollowRecordService followRecordService;

    public FollowRecordController(IFollowRecordService followRecordService) {
        this.followRecordService = followRecordService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('crm:follow:list')")
    public ApiResponse<List<CrmFollowRecord>> list(@RequestParam(required = false) Long customerId) {
        return ApiResponse.ok(followRecordService.list(customerId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('crm:customer:update')")
    public ApiResponse<Void> create(@RequestBody CrmFollowRecord record) {
        followRecordService.create(record);
        return ApiResponse.ok();
    }
}
