package com.example.crm.dto.portfolio;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PortfolioTradeAdjustmentDto {
    private String groupKey;
    private String fundCode;
    private String fundName;
    private BigDecimal buyAmount;
    private BigDecimal sellAmount;
    private BigDecimal netAmount;
    private BigDecimal currentHoldingAmount;
    private BigDecimal projectedHoldingAmount;
    private Integer transactionCount;
    private Integer skippedCount;
    private Boolean applicable;
    private List<String> warnings;
    private List<PortfolioHoldingCandidateDto> candidates;
}
