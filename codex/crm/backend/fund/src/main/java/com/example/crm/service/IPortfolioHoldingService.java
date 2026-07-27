package com.example.crm.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.crm.dto.portfolio.PortfolioHoldingBatchSummaryDto;
import com.example.crm.dto.portfolio.PortfolioHoldingConfirmRequest;
import com.example.crm.dto.portfolio.PortfolioHoldingImportPreviewResponse;
import com.example.crm.dto.portfolio.UserFundHoldingDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IPortfolioHoldingService {
    PortfolioHoldingImportPreviewResponse preview(String ownerUsername, List<MultipartFile> images);

    void confirm(String ownerUsername, Long importId, PortfolioHoldingConfirmRequest request);

    Page<UserFundHoldingDto> holdings(String ownerUsername, long current, long size, String keyword);

    Page<PortfolioHoldingBatchSummaryDto> imports(String ownerUsername, long current, long size);

    PortfolioHoldingImportPreviewResponse importDetail(String ownerUsername, Long importId);
}
