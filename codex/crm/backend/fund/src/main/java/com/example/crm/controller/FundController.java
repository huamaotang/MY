package com.example.crm.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.crm.common.ApiResponse;
import com.example.crm.dto.FundDetailResponse;
import com.example.crm.entity.CfgFund;
import com.example.crm.entity.FundFeatureData;
import com.example.crm.entity.FundNavHistory;
import com.example.crm.entity.FundRating;
import com.example.crm.entity.FundStockHolding;
import com.example.crm.service.IFundService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/funds")
public class FundController {
    private final IFundService fundService;

    public FundController(IFundService fundService) {
        this.fundService = fundService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('fund:list')")
    public ApiResponse<Page<CfgFund>> page(@RequestParam(defaultValue = "1") long current,
                                           @RequestParam(defaultValue = "10") long size,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) String fundType) {
        return ApiResponse.ok(fundService.page(current, size, keyword, fundType));
    }

    @GetMapping("/{fundCode}")
    @PreAuthorize("hasAuthority('fund:list')")
    public ApiResponse<FundDetailResponse> detail(@PathVariable String fundCode) {
        return ApiResponse.ok(fundService.detail(fundCode));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('fund:create')")
    public ApiResponse<Void> create(@RequestBody CfgFund fund) {
        fundService.create(fund);
        return ApiResponse.ok();
    }

    @PutMapping("/{fundCode}")
    @PreAuthorize("hasAuthority('fund:update')")
    public ApiResponse<Void> update(@PathVariable String fundCode, @RequestBody CfgFund fund) {
        fundService.update(fundCode, fund);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{fundCode}")
    @PreAuthorize("hasAuthority('fund:delete')")
    public ApiResponse<Void> delete(@PathVariable String fundCode) {
        fundService.delete(fundCode);
        return ApiResponse.ok();
    }

    @GetMapping("/{fundCode}/navs")
    @PreAuthorize("hasAuthority('fund:list')")
    public ApiResponse<Page<FundNavHistory>> navs(@PathVariable String fundCode,
                                                  @RequestParam(defaultValue = "1") long current,
                                                  @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(fundService.navs(fundCode, current, size));
    }

    @GetMapping("/{fundCode}/holdings")
    @PreAuthorize("hasAuthority('fund:list')")
    public ApiResponse<Page<FundStockHolding>> holdings(@PathVariable String fundCode,
                                                        @RequestParam(defaultValue = "1") long current,
                                                        @RequestParam(defaultValue = "10") long size,
                                                        @RequestParam(required = false) String reportDate) {
        return ApiResponse.ok(fundService.holdings(fundCode, current, size, reportDate));
    }

    @GetMapping("/{fundCode}/features")
    @PreAuthorize("hasAuthority('fund:list')")
    public ApiResponse<List<FundFeatureData>> features(@PathVariable String fundCode) {
        return ApiResponse.ok(fundService.features(fundCode));
    }

    @GetMapping("/{fundCode}/ratings")
    @PreAuthorize("hasAuthority('fund:list')")
    public ApiResponse<List<FundRating>> ratings(@PathVariable String fundCode) {
        return ApiResponse.ok(fundService.ratings(fundCode));
    }
}
