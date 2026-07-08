package com.example.crm.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.crm.common.ApiResponse;
import com.example.crm.dto.UserResponse;
import com.example.crm.dto.UserSaveRequest;
import com.example.crm.entity.SysUser;
import com.example.crm.mapper.SysUserMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/users")
public class UserController {
    private final SysUserMapper sysUserMapper;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public UserController(SysUserMapper sysUserMapper, JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.sysUserMapper = sysUserMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<UserResponse>> list(@RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<SysUser> query = new LambdaQueryWrapper<SysUser>()
                .and(keyword != null && !keyword.trim().isEmpty(), wrapper -> wrapper
                        .like(SysUser::getRealName, keyword)
                        .or()
                        .like(SysUser::getUsername, keyword))
                .orderByDesc(SysUser::getUpdatedAt);
        List<UserResponse> users = sysUserMapper.selectList(query).stream()
                .map(user -> UserResponse.from(user, selectRoleIds(user.getId()), selectRoleNames(user.getId())))
                .collect(Collectors.toList());
        return ApiResponse.ok(users);
    }

    @PostMapping
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> create(@RequestBody UserSaveRequest request) {
        SysUser user = new SysUser();
        fillUser(user, request, true);
        sysUserMapper.insert(user);
        saveUserRoles(user.getId(), request.getRoleIds());
        return ApiResponse.ok();
    }

    @PutMapping("/{id}")
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody UserSaveRequest request) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            return ApiResponse.fail("用户不存在");
        }
        fillUser(user, request, false);
        sysUserMapper.updateById(user);
        saveUserRoles(id, request.getRoleIds());
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        if (id == 1L) {
            return ApiResponse.fail("默认管理员不能删除");
        }
        jdbcTemplate.update("delete from sys_user_role where user_id = ?", id);
        sysUserMapper.deleteById(id);
        return ApiResponse.ok();
    }

    private void fillUser(SysUser user, UserSaveRequest request, boolean creating) {
        user.setDeptId(request.getDeptId());
        user.setUsername(request.getUsername());
        user.setRealName(request.getRealName());
        user.setMobile(request.getMobile());
        user.setEmail(request.getEmail());
        user.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        if (creating || (request.getPassword() != null && !request.getPassword().trim().isEmpty())) {
            String rawPassword = request.getPassword() == null || request.getPassword().trim().isEmpty()
                    ? "123456"
                    : request.getPassword().trim();
            user.setPassword(passwordEncoder.encode(rawPassword));
        }
    }

    private List<Long> selectRoleIds(Long userId) {
        return jdbcTemplate.queryForList("select role_id from sys_user_role where user_id = ?", Long.class, userId);
    }

    private List<String> selectRoleNames(Long userId) {
        return jdbcTemplate.queryForList(
                "select r.role_name from sys_role r inner join sys_user_role ur on ur.role_id = r.id where ur.user_id = ? order by r.id",
                String.class,
                userId);
    }

    private void saveUserRoles(Long userId, List<Long> roleIds) {
        jdbcTemplate.update("delete from sys_user_role where user_id = ?", userId);
        List<Long> ids = roleIds == null ? Collections.emptyList() : roleIds;
        for (Long roleId : ids) {
            jdbcTemplate.update("insert into sys_user_role (user_id, role_id) values (?, ?)", userId, roleId);
        }
    }
}
