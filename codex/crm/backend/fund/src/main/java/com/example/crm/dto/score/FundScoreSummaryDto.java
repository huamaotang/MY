package com.example.crm.dto.score;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class FundScoreSummaryDto {
    private Long profileId;
    private String profileName;
    private Integer profileVersion;
    private String validationStatus;
    private String asOfDate;
    private LocalDateTime updatedAt;
    private BigDecimal totalScore;
    private BigDecimal profitProbability;
    private String confidence;
    private BigDecimal dataCoverage;
    private String comparisonGroup;
    private Integer categoryRank;
    private Integer categoryCount;
    private String methodologyVersion;
}
