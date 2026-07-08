package com.example.crm.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.crm.common.ApiResponse;
import com.example.crm.entity.SysUser;
import com.example.crm.mapper.SysUserMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {
    private final SysUserMapper sysUserMapper;

    public UserController(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system:user:list')")
    public ApiResponse<List<SysUser>> list(@RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<SysUser> query = new LambdaQueryWrapper<SysUser>()
                .like(keyword != null && !keyword.trim().isEmpty(), SysUser::getRealName, keyword)
                .orderByDesc(SysUser::getUpdatedAt);
        List<SysUser> users = sysUserMapper.selectList(query);
        users.forEach(user -> user.setPassword(null));
        return ApiResponse.ok(users);
    }
}
