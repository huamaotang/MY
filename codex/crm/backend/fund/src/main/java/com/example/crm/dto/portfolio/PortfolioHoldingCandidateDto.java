package com.example.crm.dto.portfolio;

import lombok.Data;

@Data
public class PortfolioHoldingCandidateDto {
    private String fundCode;
    private String fundName;
    private Integer score;
}
