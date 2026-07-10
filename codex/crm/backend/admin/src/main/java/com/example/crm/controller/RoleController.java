package com.example.crm.controller;

import com.example.crm.common.ApiResponse;
import com.example.crm.dto.RoleResponse;
import com.example.crm.dto.RoleSaveRequest;
import com.example.crm.service.IRoleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/roles")
public class RoleController {
    private final IRoleService roleService;

    public RoleController(IRoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<RoleResponse>> list() {
        return ApiResponse.ok(roleService.list());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> create(@RequestBody RoleSaveRequest request) {
        roleService.create(request);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody RoleSaveRequest request) {
        roleService.update(id, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return ApiResponse.ok();
    }
}
