package com.example.crm.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.crm.common.ApiResponse;
import com.example.crm.entity.SysMenu;
import com.example.crm.mapper.SysMenuMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
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
    private final SysMenuMapper sysMenuMapper;

    public MenuController(SysMenuMapper sysMenuMapper) {
        this.sysMenuMapper = sysMenuMapper;
    }

    @GetMapping("/mine")
    public ApiResponse<List<SysMenu>> mine(Principal principal) {
        return ApiResponse.ok(sysMenuMapper.selectMenusByUsername(principal.getName()));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<SysMenu>> list() {
        LambdaQueryWrapper<SysMenu> query = new LambdaQueryWrapper<SysMenu>()
                .orderByAsc(SysMenu::getSortOrder)
                .orderByAsc(SysMenu::getId);
        return ApiResponse.ok(sysMenuMapper.selectList(query));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> create(@RequestBody SysMenu menu) {
        if (menu.getParentId() == null) {
            menu.setParentId(0L);
        }
        if (menu.getVisible() == null) {
            menu.setVisible(1);
        }
        sysMenuMapper.insert(menu);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody SysMenu menu) {
        menu.setId(id);
        if (menu.getParentId() == null) {
            menu.setParentId(0L);
        }
        sysMenuMapper.updateById(menu);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        Long childCount = sysMenuMapper.selectCount(new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getParentId, id));
        if (childCount != null && childCount > 0) {
            return ApiResponse.fail("请先删除子菜单");
        }
        sysMenuMapper.deleteRoleMenuByMenuId(id);
        sysMenuMapper.deleteById(id);
        return ApiResponse.ok();
    }
}
