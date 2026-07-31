package com.example.crm.controller;

import com.example.crm.common.ApiResponse;
import com.example.crm.dto.score.FundScoreBacktestDto;
import com.example.crm.dto.score.FundScoreJobDto;
import com.example.crm.dto.score.FundScoreProfileDto;
import com.example.crm.dto.score.FundScoreProfileSaveRequest;
import com.example.crm.service.IFundScoreService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/funds/scoring")
public class FundScoreController {
    private final IFundScoreService scoreService;

    public FundScoreController(IFundScoreService scoreService) {
        this.scoreService = scoreService;
    }

    @GetMapping("/profiles")
    @PreAuthorize("hasAuthority('fund:score-config')")
    public ApiResponse<List<FundScoreProfileDto>> profiles() {
        return ApiResponse.ok(scoreService.profiles());
    }

    @PostMapping("/profiles")
    @PreAuthorize("hasAuthority('fund:score-config')")
    public ApiResponse<FundScoreProfileDto> create(@RequestBody FundScoreProfileSaveRequest request,
                                                    Authentication authentication) {
        return ApiResponse.ok(scoreService.createProfile(request, authentication.getName()));
    }

    @PutMapping("/profiles/{id}")
    @PreAuthorize("hasAuthority('fund:score-config')")
    public ApiResponse<FundScoreProfileDto> update(@PathVariable Long id,
                                                    @RequestBody FundScoreProfileSaveRequest request) {
        return ApiResponse.ok(scoreService.updateProfile(id, request));
    }

    @PostMapping("/profiles/{id}/backtest")
    @PreAuthorize("hasAuthority('fund:score-config')")
    public ApiResponse<FundScoreJobDto> backtest(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.ok(scoreService.enqueueBacktest(id, authentication.getName()));
    }

    @PostMapping("/profiles/{id}/activate")
    @PreAuthorize("hasAuthority('fund:score-config')")
    public ApiResponse<Void> activate(@PathVariable Long id, Authentication authentication) {
        scoreService.activate(id, authentication.getName());
        return ApiResponse.ok();
    }

    @PostMapping("/recommend")
    @PreAuthorize("hasAuthority('fund:score-config')")
    public ApiResponse<FundScoreJobDto> recommend(Authentication authentication) {
        return ApiResponse.ok(scoreService.enqueueRecommendation(authentication.getName()));
    }

    @GetMapping("/profiles/{id}/backtest")
    @PreAuthorize("hasAuthority('fund:score-config')")
    public ApiResponse<FundScoreBacktestDto> latestBacktest(@PathVariable Long id) {
        return ApiResponse.ok(scoreService.latestBacktest(id));
    }

    @GetMapping("/jobs")
    @PreAuthorize("hasAuthority('fund:score-config')")
    public ApiResponse<List<FundScoreJobDto>> jobs() {
        return ApiResponse.ok(scoreService.jobs());
    }
}
