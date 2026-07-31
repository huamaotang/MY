package com.example.crm.dto.portfolio;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PortfolioAccountSummaryDto {
    private String sourceLabel;
    private String displayName;
    private int holdingCount;
    private BigDecimal holdingAmount;
    private BigDecimal holdingProfit;
    private BigDecimal holdingReturnRate;
    private BigDecimal todayProfit;
}
