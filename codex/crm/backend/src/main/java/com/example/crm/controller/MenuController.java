package com.example.crm.controller;

import com.example.crm.common.ApiResponse;
import com.example.crm.entity.SysMenu;
import com.example.crm.mapper.SysMenuMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/menus")
public class MenuController {
    private final SysMenuMapper sysMenuMapper;

    public MenuController(SysMenuMapper sysMenuMapper) {
        this.sysMenuMapper = sysMenuMapper;
    }

    @GetMapping("/mine")
    public ApiResponse<List<SysMenu>> mine(Principal principal) {
        return ApiResponse.ok(sysMenuMapper.selectMenusByUsername(principal.getName()));
    }
}
