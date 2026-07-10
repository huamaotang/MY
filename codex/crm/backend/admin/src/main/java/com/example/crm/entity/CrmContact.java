package com.example.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_contact")
public class CrmContact {
    private Long id;
    private Long customerId;
    private String contactName;
    private String gender;
    private String title;
    private String mobile;
    private String email;
    private String wechat;
    private Integer isPrimary;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
