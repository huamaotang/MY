package com.example.crm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.crm.entity.SysMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {
    List<SysMenu> selectMenusByUsername(@Param("username") String username);

    int deleteRoleMenuByMenuId(@Param("menuId") Long menuId);
}
