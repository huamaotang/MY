package com.example.crm.dto.portfolio;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class PortfolioHoldingConfirmRequest {
    private LocalDate screenshotDate;
    private List<PortfolioHoldingConfirmItemRequest> items;
    private List<PortfolioTradeMappingRequest> tradeMappings;
}
