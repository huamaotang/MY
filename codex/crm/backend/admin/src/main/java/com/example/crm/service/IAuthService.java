package com.example.crm.service;

import com.example.crm.dto.LoginRequest;
import com.example.crm.dto.LoginResponse;

public interface IAuthService {
    LoginResponse login(LoginRequest request);
}
