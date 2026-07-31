package com.example.crm.dto.score;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class FundScoreBacktestDto {
    private Long id;
    private Long profileId;
    private String trainStartDate;
    private String trainEndDate;
    private String testStartDate;
    private String testEndDate;
    private Integer sampleCount;
    private Integer foldCount;
    private BigDecimal auc;
    private BigDecimal brierScore;
    private BigDecimal baselineBrierScore;
    private BigDecimal top20WinRate;
    private BigDecimal baselineWinRate;
    private BigDecimal winRateLift;
    private Boolean passed;
    private String limitationsJson;
    private String metricsJson;
    private LocalDateTime createdAt;
}
