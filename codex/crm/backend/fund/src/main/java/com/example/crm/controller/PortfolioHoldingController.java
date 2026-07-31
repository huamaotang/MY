package com.example.crm.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.crm.common.ApiResponse;
import com.example.crm.dto.portfolio.PortfolioHoldingBatchSummaryDto;
import com.example.crm.dto.portfolio.PortfolioHoldingConfirmResponse;
import com.example.crm.dto.portfolio.PortfolioHoldingConfirmRequest;
import com.example.crm.dto.portfolio.PortfolioHoldingImportPreviewResponse;
import com.example.crm.dto.portfolio.PortfolioOverviewDto;
import com.example.crm.dto.portfolio.UserFundHoldingDto;
import com.example.crm.service.IPortfolioHoldingService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/portfolio")
public class PortfolioHoldingController {
    private final IPortfolioHoldingService portfolioHoldingService;

    public PortfolioHoldingController(IPortfolioHoldingService portfolioHoldingService) {
        this.portfolioHoldingService = portfolioHoldingService;
    }

    @PostMapping("/imports/ocr")
    public ApiResponse<PortfolioHoldingImportPreviewResponse> preview(Authentication authentication,
                                                                     @RequestParam(defaultValue = "alipay") String sourceLabel,
                                                                     @RequestParam(defaultValue = "holding") String importType,
                                                                     @RequestPart("images") List<MultipartFile> images) {
        return ApiResponse.ok(portfolioHoldingService.preview(
                authentication.getName(), sourceLabel, importType, images));
    }

    @PostMapping("/imports/{importId}/confirm")
    public ApiResponse<PortfolioHoldingConfirmResponse> confirm(
            Authentication authentication,
            @PathVariable Long importId,
            @org.springframework.web.bind.annotation.RequestBody(required = false)
            PortfolioHoldingConfirmRequest request) {
        return ApiResponse.ok(portfolioHoldingService.confirm(
                authentication.getName(), importId, request));
    }

    @GetMapping("/holdings")
    public ApiResponse<Page<UserFundHoldingDto>> holdings(Authentication authentication,
                                                          @RequestParam(defaultValue = "1") long current,
                                                          @RequestParam(defaultValue = "10") long size,
                                                          @RequestParam(required = false) String keyword,
                                                          @RequestParam(defaultValue = "raw") String scope,
                                                          @RequestParam(defaultValue = "holdingAmount") String sortField,
                                                          @RequestParam(defaultValue = "desc") String sortOrder) {
        return ApiResponse.ok(portfolioHoldingService.holdings(
                authentication.getName(), current, size, keyword, scope, sortField, sortOrder));
    }

    @GetMapping("/overview")
    public ApiResponse<PortfolioOverviewDto> overview(Authentication authentication) {
        return ApiResponse.ok(portfolioHoldingService.overview(authentication.getName()));
    }

    @GetMapping("/imports")
    public ApiResponse<Page<PortfolioHoldingBatchSummaryDto>> imports(Authentication authentication,
                                                                      @RequestParam(defaultValue = "1") long current,
                                                                      @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(portfolioHoldingService.imports(authentication.getName(), current, size));
    }

    @GetMapping("/imports/{importId}")
    public ApiResponse<PortfolioHoldingImportPreviewResponse> importDetail(Authentication authentication,
                                                                           @PathVariable Long importId) {
        return ApiResponse.ok(portfolioHoldingService.importDetail(authentication.getName(), importId));
    }
}
