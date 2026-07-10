package com.example.crm.controller;

import com.example.crm.common.ApiResponse;
import com.example.crm.dto.LoginRequest;
import com.example.crm.dto.LoginResponse;
import com.example.crm.service.IAuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.security.Principal;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final IAuthService authService;

    public AuthController(IAuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<Principal> me(Principal principal) {
        return ApiResponse.ok(principal);
    }
}
