package com.example.crm.controller;

import com.example.crm.common.ApiResponse;
import com.example.crm.entity.SysMenu;
import com.example.crm.service.IMenuService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/menus")
public class MenuController {
    private final IMenuService menuService;

    public MenuController(IMenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping("/mine")
    public ApiResponse<List<SysMenu>> mine(Principal principal) {
        return ApiResponse.ok(menuService.mine(principal.getName()));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<SysMenu>> list() {
        return ApiResponse.ok(menuService.list());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> create(@RequestBody SysMenu menu) {
        menuService.create(menu);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody SysMenu menu) {
        menuService.update(id, menu);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        menuService.delete(id);
        return ApiResponse.ok();
    }
}
