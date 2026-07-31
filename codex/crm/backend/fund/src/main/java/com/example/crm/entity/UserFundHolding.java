package com.example.crm.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("user_fund_holding")
public class UserFundHolding {
    private Long id;
    private String ownerUsername;
    private String sourceLabel;
    private String fundCode;
    private String fundName;
    private BigDecimal holdingAmount;
    private BigDecimal holdingProfit;
    private BigDecimal holdingReturnRate;
    private BigDecimal holdingCost;
    private BigDecimal yesterdayProfit;
    private BigDecimal todayProfit;
    private BigDecimal holdingShares;
    private BigDecimal costNav;
    private LocalDate screenshotDate;
    private Long latestImportId;
    private LocalDateTime latestImportAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private FundHoldingImportBatch latestImport;
}
