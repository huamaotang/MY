package com.example.crm.dto.portfolio;

import lombok.Data;

import java.util.List;

@Data
public class PortfolioHoldingConfirmResponse {
    private Integer affectedHoldingCount;
    private Integer appliedTransactionCount;
    private Integer skippedTransactionCount;
    private List<String> warnings;
}
