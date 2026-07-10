package com.example.crm.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.crm.common.ApiResponse;
import com.example.crm.entity.CrmCustomer;
import com.example.crm.mapper.CrmCustomerMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/customers")
public class CustomerController {
    private final CrmCustomerMapper customerMapper;

    public CustomerController(CrmCustomerMapper customerMapper) {
        this.customerMapper = customerMapper;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('crm:customer:list')")
    public ApiResponse<Page<CrmCustomer>> page(@RequestParam(defaultValue = "1") long current,
                                               @RequestParam(defaultValue = "10") long size,
                                               @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<CrmCustomer> query = new LambdaQueryWrapper<CrmCustomer>()
                .like(keyword != null && !keyword.trim().isEmpty(), CrmCustomer::getCustomerName, keyword)
                .orderByDesc(CrmCustomer::getUpdatedAt);
        return ApiResponse.ok(customerMapper.selectPage(new Page<>(current, size), query));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('crm:customer:list')")
    public ApiResponse<CrmCustomer> detail(@PathVariable Long id) {
        return ApiResponse.ok(customerMapper.selectById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('crm:customer:create')")
    public ApiResponse<Void> create(@RequestBody CrmCustomer customer) {
        customerMapper.insert(customer);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('crm:customer:update')")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody CrmCustomer customer) {
        customer.setId(id);
        customerMapper.updateById(customer);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('crm:customer:delete')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        customerMapper.deleteById(id);
        return ApiResponse.ok();
    }
}
