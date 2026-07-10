package com.example.crm.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.crm.common.ApiResponse;
import com.example.crm.entity.CrmCustomer;
import com.example.crm.service.ICustomerService;
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
    private final ICustomerService customerService;

    public CustomerController(ICustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('crm:customer:list')")
    public ApiResponse<Page<CrmCustomer>> page(@RequestParam(defaultValue = "1") long current,
                                               @RequestParam(defaultValue = "10") long size,
                                               @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(customerService.page(current, size, keyword));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('crm:customer:list')")
    public ApiResponse<CrmCustomer> detail(@PathVariable Long id) {
        return ApiResponse.ok(customerService.detail(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('crm:customer:create')")
    public ApiResponse<Void> create(@RequestBody CrmCustomer customer) {
        customerService.create(customer);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('crm:customer:update')")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody CrmCustomer customer) {
        customerService.update(id, customer);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('crm:customer:delete')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        customerService.delete(id);
        return ApiResponse.ok();
    }
}
