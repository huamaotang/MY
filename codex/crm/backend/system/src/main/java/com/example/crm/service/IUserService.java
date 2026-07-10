package com.example.crm.service;

import com.example.crm.dto.UserResponse;
import com.example.crm.dto.UserSaveRequest;

import java.util.List;

public interface IUserService {
    List<UserResponse> list(String keyword);

    void create(UserSaveRequest request);

    void update(Long id, UserSaveRequest request);

    void delete(Long id);
}
