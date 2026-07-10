package com.example.crm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.crm.common.BusinessException;
import com.example.crm.dto.UserResponse;
import com.example.crm.dto.UserSaveRequest;
import com.example.crm.entity.SysUser;
import com.example.crm.mapper.SysUserMapper;
import com.example.crm.service.IUserService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements IUserService {
    private final SysUserMapper sysUserMapper;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(SysUserMapper sysUserMapper, JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.sysUserMapper = sysUserMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public List<UserResponse> list(String keyword) {
        LambdaQueryWrapper<SysUser> query = new LambdaQueryWrapper<SysUser>()
                .and(keyword != null && !keyword.trim().isEmpty(), wrapper -> wrapper
                        .like(SysUser::getRealName, keyword)
                        .or()
                        .like(SysUser::getUsername, keyword))
                .orderByDesc(SysUser::getUpdatedAt);
        return sysUserMapper.selectList(query).stream()
                .map(user -> UserResponse.from(user, selectRoleIds(user.getId()), selectRoleNames(user.getId())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void create(UserSaveRequest request) {
        SysUser user = new SysUser();
        fillUser(user, request, true);
        sysUserMapper.insert(user);
        saveUserRoles(user.getId(), request.getRoleIds());
    }

    @Override
    @Transactional
    public void update(Long id, UserSaveRequest request) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        fillUser(user, request, false);
        sysUserMapper.updateById(user);
        saveUserRoles(id, request.getRoleIds());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (id == 1L) {
            throw new BusinessException("默认管理员不能删除");
        }
        jdbcTemplate.update("delete from sys_user_role where user_id = ?", id);
        sysUserMapper.deleteById(id);
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
