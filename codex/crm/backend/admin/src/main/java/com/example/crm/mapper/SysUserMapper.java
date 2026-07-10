package com.example.crm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.crm.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
    @Select("select * from sys_user where username = #{username} limit 1")
    SysUser findByUsername(String username);

    @Select("select r.role_code from sys_role r inner join sys_user_role ur on ur.role_id = r.id where ur.user_id = #{userId} and r.status = 1")
    List<String> selectRoleCodes(Long userId);

    @Select("select distinct m.permission_code from sys_menu m inner join sys_role_menu rm on rm.menu_id = m.id inner join sys_user_role ur on ur.role_id = rm.role_id where ur.user_id = #{userId} and m.visible = 1 and m.permission_code is not null")
    List<String> selectPermissionCodes(Long userId);
}
