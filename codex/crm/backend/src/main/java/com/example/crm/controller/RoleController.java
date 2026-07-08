package com.example.crm.controller;

import com.example.crm.common.ApiResponse;
import com.example.crm.entity.SysRole;
import com.example.crm.mapper.SysRoleMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/roles")
public class RoleController {
    private final SysRoleMapper sysRoleMapper;

    public RoleController(SysRoleMapper sysRoleMapper) {
        this.sysRoleMapper = sysRoleMapper;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system:role:list')")
    public ApiResponse<List<SysRole>> list() {
        return ApiResponse.ok(sysRoleMapper.selectList(null));
    }
}
