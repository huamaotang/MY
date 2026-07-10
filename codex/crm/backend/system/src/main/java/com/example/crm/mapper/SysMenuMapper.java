package com.example.crm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.crm.entity.SysMenu;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {
    @Select("select distinct m.* from sys_menu m inner join sys_role_menu rm on rm.menu_id = m.id inner join sys_user_role ur on ur.role_id = rm.role_id inner join sys_user u on u.id = ur.user_id where u.username = #{username} and m.visible = 1 order by m.sort_order asc, m.id asc")
    List<SysMenu> selectMenusByUsername(String username);

    @Delete("delete from sys_role_menu where menu_id = #{menuId}")
    int deleteRoleMenuByMenuId(Long menuId);
}
