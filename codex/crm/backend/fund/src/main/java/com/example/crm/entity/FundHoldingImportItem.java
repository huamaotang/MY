package com.example.crm.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("fund_holding_import_item")
public class FundHoldingImportItem {
    private Long id;
    private Long importId;
    private Integer rowNo;
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
    private BigDecimal confidence;
    private String candidateJson;
    private String rawTextJson;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private java.util.List<FundHoldingCandidate> candidates;
}
