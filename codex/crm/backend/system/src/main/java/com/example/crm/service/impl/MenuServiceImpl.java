package com.example.crm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.crm.common.BusinessException;
import com.example.crm.entity.SysMenu;
import com.example.crm.mapper.SysMenuMapper;
import com.example.crm.service.IMenuService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MenuServiceImpl implements IMenuService {
    private final SysMenuMapper sysMenuMapper;

    public MenuServiceImpl(SysMenuMapper sysMenuMapper) {
        this.sysMenuMapper = sysMenuMapper;
    }

    @Override
    public List<SysMenu> mine(String username) {
        return sysMenuMapper.selectMenusByUsername(username);
    }

    @Override
    public List<SysMenu> list() {
        LambdaQueryWrapper<SysMenu> query = new LambdaQueryWrapper<SysMenu>()
                .orderByAsc(SysMenu::getSortOrder)
                .orderByAsc(SysMenu::getId);
        return sysMenuMapper.selectList(query);
    }

    @Override
    public void create(SysMenu menu) {
        if (menu.getParentId() == null) {
            menu.setParentId(0L);
        }
        if (menu.getVisible() == null) {
            menu.setVisible(1);
        }
        sysMenuMapper.insert(menu);
    }

    @Override
    public void update(Long id, SysMenu menu) {
        menu.setId(id);
        if (menu.getParentId() == null) {
            menu.setParentId(0L);
        }
        sysMenuMapper.updateById(menu);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Long childCount = sysMenuMapper.selectCount(new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BusinessException("请先删除子菜单");
        }
        sysMenuMapper.deleteRoleMenuByMenuId(id);
        sysMenuMapper.deleteById(id);
    }
}
