package com.example.crm.security;

import com.example.crm.entity.SysUser;
import com.example.crm.mapper.SysUserMapper;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CrmUserDetailsService implements UserDetailsService {
    private final SysUserMapper sysUserMapper;

    public CrmUserDetailsService(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = sysUserMapper.findByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }
        List<String> permissions = new ArrayList<>();
        permissions.addAll(sysUserMapper.selectRoleCodes(user.getId()).stream()
                .map(role -> "ROLE_" + role)
                .collect(Collectors.toList()));
        permissions.addAll(sysUserMapper.selectPermissionCodes(user.getId()));
        return User.withUsername(user.getUsername())
                .password(user.getPassword())
                .disabled(user.getStatus() == null || user.getStatus() != 1)
                .authorities(permissions.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList()))
                .build();
    }
}
