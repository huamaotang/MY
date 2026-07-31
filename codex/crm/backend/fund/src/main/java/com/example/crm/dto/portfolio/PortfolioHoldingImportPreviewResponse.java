package com.example.crm.dto.portfolio;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class PortfolioHoldingImportPreviewResponse {
    private Long importId;
    private String sourceLabel;
    private String importType;
    private String status;
    private LocalDate screenshotDate;
    private Integer imageCount;
    private List<String> imageHashes;
    private List<String> warnings;
    private List<PortfolioHoldingImportRowDto> rows;
    private List<PortfolioTradeAdjustmentDto> tradeAdjustments;
}
