package com.example.crm.controller;

import com.example.crm.common.ApiResponse;
import com.example.crm.dto.RoleResponse;
import com.example.crm.dto.RoleSaveRequest;
import com.example.crm.entity.SysRole;
import com.example.crm.mapper.SysRoleMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/roles")
public class RoleController {
    private final SysRoleMapper sysRoleMapper;
    private final JdbcTemplate jdbcTemplate;

    public RoleController(SysRoleMapper sysRoleMapper, JdbcTemplate jdbcTemplate) {
        this.sysRoleMapper = sysRoleMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<RoleResponse>> list() {
        List<RoleResponse> roles = sysRoleMapper.selectList(null).stream()
                .map(role -> RoleResponse.from(role, selectMenuIds(role.getId())))
                .collect(Collectors.toList());
        return ApiResponse.ok(roles);
    }

    @PostMapping
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> create(@RequestBody RoleSaveRequest request) {
        SysRole role = new SysRole();
        fillRole(role, request);
        sysRoleMapper.insert(role);
        saveRoleMenus(role.getId(), request.getMenuIds());
        return ApiResponse.ok();
    }

    @PutMapping("/{id}")
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody RoleSaveRequest request) {
        SysRole role = sysRoleMapper.selectById(id);
        if (role == null) {
            return ApiResponse.fail("角色不存在");
        }
        fillRole(role, request);
        sysRoleMapper.updateById(role);
        saveRoleMenus(id, request.getMenuIds());
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        if (id == 1L) {
            return ApiResponse.fail("默认管理员角色不能删除");
        }
        jdbcTemplate.update("delete from sys_role_menu where role_id = ?", id);
        jdbcTemplate.update("delete from sys_user_role where role_id = ?", id);
        sysRoleMapper.deleteById(id);
        return ApiResponse.ok();
    }

    private void fillRole(SysRole role, RoleSaveRequest request) {
        role.setRoleName(request.getRoleName());
        role.setRoleCode(request.getRoleCode());
        role.setDataScope(request.getDataScope() == null ? "ALL" : request.getDataScope());
        role.setStatus(request.getStatus() == null ? 1 : request.getStatus());
    }

    private List<Long> selectMenuIds(Long roleId) {
        return jdbcTemplate.queryForList("select menu_id from sys_role_menu where role_id = ?", Long.class, roleId);
    }

    private void saveRoleMenus(Long roleId, List<Long> menuIds) {
        jdbcTemplate.update("delete from sys_role_menu where role_id = ?", roleId);
        List<Long> ids = menuIds == null ? Collections.emptyList() : menuIds;
        for (Long menuId : ids) {
            jdbcTemplate.update("insert into sys_role_menu (role_id, menu_id) values (?, ?)", roleId, menuId);
        }
    }
}
