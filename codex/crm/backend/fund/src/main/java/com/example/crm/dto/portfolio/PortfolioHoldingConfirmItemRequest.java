package com.example.crm.dto.portfolio;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class PortfolioHoldingConfirmItemRequest {
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
    private List<String> rawTexts;
}
