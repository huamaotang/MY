package com.example.crm.service;

import com.example.crm.dto.RoleResponse;
import com.example.crm.dto.RoleSaveRequest;

import java.util.List;

public interface IRoleService {
    List<RoleResponse> list();

    void create(RoleSaveRequest request);

    void update(Long id, RoleSaveRequest request);

    void delete(Long id);
}
