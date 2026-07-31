package com.example.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("fund_holding_trade_import_item")
public class FundHoldingTradeImportItem {
    private Long id;
    private Long importId;
    private Integer rowNo;
    private String groupKey;
    private String fundCode;
    private String fundName;
    private String operationType;
    private BigDecimal transactionAmount;
    private LocalDateTime transactionAt;
    private String transactionStatus;
    private LocalDate screenshotDate;
    private BigDecimal confidence;
    private String fingerprint;
    private String appliedKey;
    private String status;
    private String skipReason;
    private BigDecimal beforeHoldingAmount;
    private BigDecimal afterHoldingAmount;
    private String rawTextJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
