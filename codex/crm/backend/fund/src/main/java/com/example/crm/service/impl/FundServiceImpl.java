package com.example.crm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.crm.common.BusinessException;
import com.example.crm.dto.FundDetailResponse;
import com.example.crm.entity.CfgFund;
import com.example.crm.entity.FundFeatureData;
import com.example.crm.entity.FundNavHistory;
import com.example.crm.entity.FundStockHolding;
import com.example.crm.mapper.CfgFundMapper;
import com.example.crm.mapper.FundFeatureDataMapper;
import com.example.crm.mapper.FundNavHistoryMapper;
import com.example.crm.mapper.FundStockHoldingMapper;
import com.example.crm.service.IFundService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FundServiceImpl implements IFundService {
    private static final long DEFAULT_DETAIL_HOLDING_SIZE = 10;

    private final CfgFundMapper fundMapper;
    private final FundNavHistoryMapper navHistoryMapper;
    private final FundStockHoldingMapper stockHoldingMapper;
    private final FundFeatureDataMapper featureDataMapper;

    public FundServiceImpl(CfgFundMapper fundMapper,
                           FundNavHistoryMapper navHistoryMapper,
                           FundStockHoldingMapper stockHoldingMapper,
                           FundFeatureDataMapper featureDataMapper) {
        this.fundMapper = fundMapper;
        this.navHistoryMapper = navHistoryMapper;
        this.stockHoldingMapper = stockHoldingMapper;
        this.featureDataMapper = featureDataMapper;
    }

    @Override
    public Page<CfgFund> page(long current, long size, String keyword, String fundType) {
        LambdaQueryWrapper<CfgFund> query = new LambdaQueryWrapper<CfgFund>()
                .and(hasText(keyword), wrapper -> wrapper
                        .like(CfgFund::getFundName, keyword.trim())
                        .or()
                        .like(CfgFund::getFundCode, keyword.trim())
                        .or()
                        .like(CfgFund::getFundManager, keyword.trim()))
                .eq(hasText(fundType), CfgFund::getFundType, fundType)
                .orderByAsc(CfgFund::getFundCode);
        return fundMapper.selectPage(new Page<>(current, size), query);
    }

    @Override
    public FundDetailResponse detail(String fundCode) {
        CfgFund fund = findFund(fundCode);
        FundDetailResponse response = new FundDetailResponse();
        response.setFund(fund);
        response.setLatestNav(latestNav(fundCode));
        response.setLatestHoldings(latestHoldings(fundCode));
        response.setFeatures(features(fundCode));
        return response;
    }

    @Override
    public void create(CfgFund fund) {
        validateFund(fund);
        if (findFundOrNull(fund.getFundCode()) != null) {
            throw new BusinessException("基金代码已存在");
        }
        fundMapper.insert(fund);
    }

    @Override
    public void update(String fundCode, CfgFund fund) {
        findFund(fundCode);
        if (fund == null || !hasText(fund.getFundName())) {
            throw new BusinessException("基金名称不能为空");
        }
        fund.setFundCode(fundCode);
        fundMapper.update(fund, new LambdaQueryWrapper<CfgFund>().eq(CfgFund::getFundCode, fundCode));
    }

    @Override
    public void delete(String fundCode) {
        findFund(fundCode);
        fundMapper.delete(new LambdaQueryWrapper<CfgFund>().eq(CfgFund::getFundCode, fundCode));
    }

    @Override
    public Page<FundNavHistory> navs(String fundCode, long current, long size) {
        findFund(fundCode);
        return navHistoryMapper.selectPage(new Page<>(current, size), new LambdaQueryWrapper<FundNavHistory>()
                .eq(FundNavHistory::getFundCode, fundCode)
                .orderByDesc(FundNavHistory::getNavDate));
    }

    @Override
    public Page<FundStockHolding> holdings(String fundCode, long current, long size, String reportDate) {
        findFund(fundCode);
        LambdaQueryWrapper<FundStockHolding> query = new LambdaQueryWrapper<FundStockHolding>()
                .eq(FundStockHolding::getFundCode, fundCode)
                .eq(hasText(reportDate), FundStockHolding::getReportDate, reportDate)
                .orderByDesc(FundStockHolding::getReportDate)
                .orderByAsc(FundStockHolding::getRankNo);
        return stockHoldingMapper.selectPage(new Page<>(current, size), query);
    }

    @Override
    public List<FundFeatureData> features(String fundCode) {
        findFund(fundCode);
        return featureDataMapper.selectList(new LambdaQueryWrapper<FundFeatureData>()
                .eq(FundFeatureData::getFundCode, fundCode)
                .orderByDesc(FundFeatureData::getCutoffDate)
                .orderByAsc(FundFeatureData::getPeriodLabel));
    }

    private CfgFund findFund(String fundCode) {
        CfgFund fund = findFundOrNull(fundCode);
        if (fund == null) {
            throw new BusinessException("基金不存在");
        }
        return fund;
    }

    private CfgFund findFundOrNull(String fundCode) {
        if (!hasText(fundCode)) {
            return null;
        }
        return fundMapper.selectOne(new LambdaQueryWrapper<CfgFund>().eq(CfgFund::getFundCode, fundCode));
    }

    private FundNavHistory latestNav(String fundCode) {
        return navHistoryMapper.selectOne(new LambdaQueryWrapper<FundNavHistory>()
                .eq(FundNavHistory::getFundCode, fundCode)
                .orderByDesc(FundNavHistory::getNavDate)
                .last("limit 1"));
    }

    private List<FundStockHolding> latestHoldings(String fundCode) {
        FundStockHolding latest = stockHoldingMapper.selectOne(new LambdaQueryWrapper<FundStockHolding>()
                .eq(FundStockHolding::getFundCode, fundCode)
                .orderByDesc(FundStockHolding::getReportDate)
                .last("limit 1"));
        if (latest == null || !hasText(latest.getReportDate())) {
            return java.util.Collections.emptyList();
        }
        return stockHoldingMapper.selectPage(new Page<>(1, DEFAULT_DETAIL_HOLDING_SIZE), new LambdaQueryWrapper<FundStockHolding>()
                .eq(FundStockHolding::getFundCode, fundCode)
                .eq(FundStockHolding::getReportDate, latest.getReportDate())
                .orderByAsc(FundStockHolding::getRankNo)).getRecords();
    }

    private void validateFund(CfgFund fund) {
        if (fund == null || !hasText(fund.getFundCode())) {
            throw new BusinessException("基金代码不能为空");
        }
        if (!hasText(fund.getFundName())) {
            throw new BusinessException("基金名称不能为空");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
