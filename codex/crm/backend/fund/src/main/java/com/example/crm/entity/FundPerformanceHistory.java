package com.example.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("fund_performance_history")
public class FundPerformanceHistory {
    private Long id;
    private String fundCode;
    private String navDate;
    private String fundNamePinyin;
    private LocalDate inceptionDate;
    private BigDecimal weeklyReturnRate;
    private BigDecimal monthlyReturnRate;
    private BigDecimal threeMonthReturnRate;
    private BigDecimal sixMonthReturnRate;
    private BigDecimal oneYearReturnRate;
    private BigDecimal twoYearReturnRate;
    private BigDecimal threeYearReturnRate;
    private BigDecimal yearToDateReturnRate;
    private BigDecimal sinceInceptionReturnRate;
    private LocalDate customStartDate;
    private LocalDate customEndDate;
    private BigDecimal customReturnRate;
    private String saleStatus;
    private BigDecimal originalFeeRate;
    private BigDecimal discountedFeeRate;
    private BigDecimal discountFactor;
    private BigDecimal cashManagementFeeRate;
    @JsonIgnore
    private String sourceRow;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
