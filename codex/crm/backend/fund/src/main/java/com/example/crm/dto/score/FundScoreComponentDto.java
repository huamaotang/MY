package com.example.crm.dto.score;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class FundScoreComponentDto {
    private String factorKey;
    private String label;
    private BigDecimal rawValue;
    private BigDecimal normalizedScore;
    private Integer weight;
    private BigDecimal effectiveWeight;
    private BigDecimal contribution;
}
