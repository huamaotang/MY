package com.example.crm.service;

import com.example.crm.entity.SysMenu;

import java.util.List;

public interface IMenuService {
    List<SysMenu> mine(String username);

    List<SysMenu> list();

    void create(SysMenu menu);

    void update(Long id, SysMenu menu);

    void delete(Long id);
}
