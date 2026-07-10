package com.example.crm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.crm.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
    SysUser findByUsername(@Param("username") String username);

    List<String> selectRoleCodes(@Param("userId") Long userId);

    List<String> selectPermissionCodes(@Param("userId") Long userId);
}
