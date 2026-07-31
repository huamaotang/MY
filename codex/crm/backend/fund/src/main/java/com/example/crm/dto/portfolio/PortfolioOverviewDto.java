package com.example.crm.dto.portfolio;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PortfolioOverviewDto {
    private PortfolioAccountSummaryDto total;
    private List<PortfolioAccountSummaryDto> accounts = new ArrayList<>();
}
