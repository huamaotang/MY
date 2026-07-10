package com.example.crm.controller;

import com.example.crm.common.ApiResponse;
import com.example.crm.dto.UserResponse;
import com.example.crm.dto.UserSaveRequest;
import com.example.crm.service.IUserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {
    private final IUserService userService;

    public UserController(IUserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<UserResponse>> list(@RequestParam(required = false) String keyword) {
        return ApiResponse.ok(userService.list(keyword));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> create(@RequestBody UserSaveRequest request) {
        userService.create(request);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody UserSaveRequest request) {
        userService.update(id, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return ApiResponse.ok();
    }
}
