package com.example.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("cfg_fund")
public class CfgFund {
    private Long id;
    private String fundCode;
    private String fundName;
    private LocalDate inceptionDate;
    private String fundManager;
    private String fundType;
    private String managementCompany;
    private String netAssetScale;
    private LocalDate scaleDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
