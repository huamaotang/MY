package com.example.crm.dto.portfolio;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class PortfolioHoldingBatchSummaryDto {
    private Long id;
    private String status;
    private String sourceLabel;
    private String importType;
    private LocalDate screenshotDate;
    private Integer imageCount;
    private Integer itemCount;
    private Integer transactionCount;
    private Integer appliedCount;
    private Integer skippedCount;
    private LocalDateTime confirmedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
