package com.example.crm.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.crm.common.ApiResponse;
import com.example.crm.entity.CrmContact;
import com.example.crm.mapper.CrmContactMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/contacts")
public class ContactController {
    private final CrmContactMapper contactMapper;

    public ContactController(CrmContactMapper contactMapper) {
        this.contactMapper = contactMapper;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('crm:contact:list')")
    public ApiResponse<List<CrmContact>> list(@RequestParam(required = false) Long customerId) {
        LambdaQueryWrapper<CrmContact> query = new LambdaQueryWrapper<CrmContact>()
                .eq(customerId != null, CrmContact::getCustomerId, customerId)
                .orderByDesc(CrmContact::getUpdatedAt);
        return ApiResponse.ok(contactMapper.selectList(query));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('crm:customer:update')")
    public ApiResponse<Void> create(@RequestBody CrmContact contact) {
        contactMapper.insert(contact);
        return ApiResponse.ok();
    }
}
