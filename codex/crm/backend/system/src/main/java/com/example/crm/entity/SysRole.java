package com.example.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_role")
public class SysRole {
    private Long id;
    private String roleName;
    private String roleCode;
    private String dataScope;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
