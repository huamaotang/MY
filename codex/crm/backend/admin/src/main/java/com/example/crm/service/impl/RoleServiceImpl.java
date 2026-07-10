package com.example.crm.service.impl;

import com.example.crm.common.BusinessException;
import com.example.crm.dto.RoleResponse;
import com.example.crm.dto.RoleSaveRequest;
import com.example.crm.entity.SysRole;
import com.example.crm.mapper.SysRoleMapper;
import com.example.crm.service.IRoleService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RoleServiceImpl implements IRoleService {
    private final SysRoleMapper sysRoleMapper;
    private final JdbcTemplate jdbcTemplate;

    public RoleServiceImpl(SysRoleMapper sysRoleMapper, JdbcTemplate jdbcTemplate) {
        this.sysRoleMapper = sysRoleMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<RoleResponse> list() {
        return sysRoleMapper.selectList(null).stream()
                .map(role -> RoleResponse.from(role, selectMenuIds(role.getId())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void create(RoleSaveRequest request) {
        SysRole role = new SysRole();
        fillRole(role, request);
        sysRoleMapper.insert(role);
        saveRoleMenus(role.getId(), request.getMenuIds());
    }

    @Override
    @Transactional
    public void update(Long id, RoleSaveRequest request) {
        SysRole role = sysRoleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        fillRole(role, request);
        sysRoleMapper.updateById(role);
        saveRoleMenus(id, request.getMenuIds());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (id == 1L) {
            throw new BusinessException("默认管理员角色不能删除");
        }
        jdbcTemplate.update("delete from sys_role_menu where role_id = ?", id);
        jdbcTemplate.update("delete from sys_user_role where role_id = ?", id);
        sysRoleMapper.deleteById(id);
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
