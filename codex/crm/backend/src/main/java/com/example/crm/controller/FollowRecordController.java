package com.example.crm.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.crm.common.ApiResponse;
import com.example.crm.entity.CrmFollowRecord;
import com.example.crm.mapper.CrmFollowRecordMapper;
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
    private final CrmFollowRecordMapper followRecordMapper;

    public FollowRecordController(CrmFollowRecordMapper followRecordMapper) {
        this.followRecordMapper = followRecordMapper;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('crm:follow:list')")
    public ApiResponse<List<CrmFollowRecord>> list(@RequestParam(required = false) Long customerId) {
        LambdaQueryWrapper<CrmFollowRecord> query = new LambdaQueryWrapper<CrmFollowRecord>()
                .eq(customerId != null, CrmFollowRecord::getCustomerId, customerId)
                .orderByDesc(CrmFollowRecord::getCreatedAt);
        return ApiResponse.ok(followRecordMapper.selectList(query));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('crm:customer:update')")
    public ApiResponse<Void> create(@RequestBody CrmFollowRecord record) {
        followRecordMapper.insert(record);
        return ApiResponse.ok();
    }
}
