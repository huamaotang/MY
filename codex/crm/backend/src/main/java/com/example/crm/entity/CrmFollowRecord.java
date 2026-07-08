package com.example.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_follow_record")
public class CrmFollowRecord {
    private Long id;
    private Long customerId;
    private Long contactId;
    private String followType;
    private String content;
    private LocalDateTime nextFollowAt;
    private Long ownerUserId;
    private LocalDateTime createdAt;
}
