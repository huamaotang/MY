package com.example.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_customer")
public class CrmCustomer {
    private Long id;
    private String customerName;
    private String customerType;
    private String industry;
    private String source;
    private String level;
    private String status;
    private Long ownerUserId;
    private String phone;
    private String email;
    private String province;
    private String city;
    private String address;
    private String remark;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
