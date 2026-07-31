package com.example.crm.service;

import com.example.crm.dto.score.FundScoreBacktestDto;
import com.example.crm.dto.score.FundScoreDetailDto;
import com.example.crm.dto.score.FundScoreJobDto;
import com.example.crm.dto.score.FundScoreProfileDto;
import com.example.crm.dto.score.FundScoreProfileSaveRequest;
import com.example.crm.dto.score.FundScoreSummaryDto;

import java.util.List;
import java.util.Map;

public interface IFundScoreService {
    Map<String, FundScoreSummaryDto> latestSummaries(List<String> fundCodes);

    FundScoreDetailDto detail(String fundCode);

    List<FundScoreProfileDto> profiles();

    FundScoreProfileDto createProfile(FundScoreProfileSaveRequest request, String username);

    FundScoreProfileDto updateProfile(Long id, FundScoreProfileSaveRequest request);

    FundScoreJobDto enqueueBacktest(Long profileId, String username);

    FundScoreJobDto enqueueRecommendation(String username);

    List<FundScoreJobDto> jobs();

    FundScoreBacktestDto latestBacktest(Long profileId);

    void activate(Long profileId, String username);
}
