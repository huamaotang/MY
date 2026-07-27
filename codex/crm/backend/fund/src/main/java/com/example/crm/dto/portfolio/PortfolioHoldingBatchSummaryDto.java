package com.example.crm.dto.portfolio;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class PortfolioHoldingBatchSummaryDto {
    private Long id;
    private String status;
    private String sourceLabel;
    private LocalDate screenshotDate;
    private Integer imageCount;
    private Integer itemCount;
    private LocalDateTime confirmedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
