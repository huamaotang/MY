package com.example.crm.service.impl;

import com.example.crm.dto.LoginRequest;
import com.example.crm.dto.LoginResponse;
import com.example.crm.entity.SysUser;
import com.example.crm.mapper.SysUserMapper;
import com.example.crm.security.JwtTokenProvider;
import com.example.crm.service.IAuthService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AuthServiceImpl implements IAuthService {
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final SysUserMapper sysUserMapper;

    public AuthServiceImpl(AuthenticationManager authenticationManager,
                           JwtTokenProvider jwtTokenProvider,
                           SysUserMapper sysUserMapper) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        SysUser user = sysUserMapper.findByUsername(request.getUsername());
        List<String> permissions = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
        String token = jwtTokenProvider.createToken(user.getId(), user.getUsername());
        return new LoginResponse(token, user.getUsername(), permissions);
    }
}
