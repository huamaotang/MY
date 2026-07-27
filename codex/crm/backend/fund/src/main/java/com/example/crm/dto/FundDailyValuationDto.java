package com.example.crm.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class FundDailyValuationDto {
    private String fundCode;
    private LocalDate valuationDate;
    private String holdingReportDate;
    private String baseNavDate;
    private BigDecimal baseUnitNav;
    private BigDecimal estimatedUnitNav;
    private BigDecimal estimatedChangeRate;
    private BigDecimal holdingWeight;
    private BigDecimal quotedHoldingWeight;
    private BigDecimal quoteCoverageRate;
    private Integer holdingCount;
    private Integer quotedHoldingCount;
    private LocalDateTime quoteUpdatedAt;
}
