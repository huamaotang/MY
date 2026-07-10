package com.example.crm.controller;

import com.example.crm.common.ApiResponse;
import com.example.crm.dto.LoginRequest;
import com.example.crm.dto.LoginResponse;
import com.example.crm.entity.SysUser;
import com.example.crm.mapper.SysUserMapper;
import com.example.crm.security.JwtTokenProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final SysUserMapper sysUserMapper;

    public AuthController(AuthenticationManager authenticationManager, JwtTokenProvider jwtTokenProvider, SysUserMapper sysUserMapper) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.sysUserMapper = sysUserMapper;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        SysUser user = sysUserMapper.findByUsername(request.getUsername());
        String token = jwtTokenProvider.createToken(user.getId(), user.getUsername());
        List<String> permissions = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
        return ApiResponse.ok(new LoginResponse(token, user.getUsername(), permissions));
    }

    @GetMapping("/me")
    public ApiResponse<Principal> me(Principal principal) {
        return ApiResponse.ok(principal);
    }
}
