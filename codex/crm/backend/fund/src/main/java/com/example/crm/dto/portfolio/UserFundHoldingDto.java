package com.example.crm.dto.portfolio;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class UserFundHoldingDto {
    private Long id;
    private String ownerUsername;
    private String sourceLabel;
    private String fundCode;
    private String fundName;
    private String fundType;
    private BigDecimal holdingAmount;
    private BigDecimal holdingProfit;
    private BigDecimal holdingReturnRate;
    private BigDecimal holdingCost;
    private BigDecimal yesterdayProfit;
    private BigDecimal todayProfit;
    private BigDecimal holdingShares;
    private BigDecimal costNav;
    private LocalDate valuationDate;
    private String holdingReportDate;
    private String holdingCutoffDate;
    private BigDecimal estimatedChangeRate;
    private BigDecimal estimatedDailyProfit;
    private BigDecimal estimatedHoldingAmount;
    private BigDecimal estimatedUnitNav;
    private BigDecimal estimatedCumulativeChangeRate;
    private BigDecimal estimatedCumulativeProfit;
    private BigDecimal valuationCoverageRate;
    private LocalDateTime valuationUpdatedAt;
    private LocalDate screenshotDate;
    private Long latestImportId;
    private LocalDateTime latestImportAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
