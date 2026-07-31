package com.example.crm.dto.score;

import lombok.Data;

import java.util.List;

@Data
public class FundScoreDetailDto {
    private FundScoreSummaryDto summary;
    private List<FundScoreComponentDto> components;
    private String disclaimer;
}
