package com.example.crm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.crm.common.BusinessException;
import com.example.crm.dto.FundDailyValuationDto;
import com.example.crm.dto.FundDetailResponse;
import com.example.crm.entity.CfgFund;
import com.example.crm.entity.FundFeatureData;
import com.example.crm.entity.FundNavHistory;
import com.example.crm.entity.FundPerformanceHistory;
import com.example.crm.entity.FundRating;
import com.example.crm.entity.FundStockHolding;
import com.example.crm.mapper.CfgFundMapper;
import com.example.crm.mapper.FundFeatureDataMapper;
import com.example.crm.mapper.FundNavHistoryMapper;
import com.example.crm.mapper.FundPerformanceHistoryMapper;
import com.example.crm.mapper.FundRatingMapper;
import com.example.crm.mapper.FundStockHoldingMapper;
import com.example.crm.service.IFundService;
import com.example.crm.service.IFundScoreService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class FundServiceImpl implements IFundService {
    private static final long DEFAULT_DETAIL_HOLDING_SIZE = 10;
    private static final long DEFAULT_DETAIL_RATING_SIZE = 12;
    private static final Map<String, String> SORT_FIELDS = sortFields();

    private final CfgFundMapper fundMapper;
    private final FundNavHistoryMapper navHistoryMapper;
    private final FundPerformanceHistoryMapper performanceHistoryMapper;
    private final FundStockHoldingMapper stockHoldingMapper;
    private final FundFeatureDataMapper featureDataMapper;
    private final FundRatingMapper ratingMapper;
    private final FundValuationService valuationService;
    private final IFundScoreService fundScoreService;
    private final JdbcTemplate jdbcTemplate;

    public FundServiceImpl(CfgFundMapper fundMapper,
                           FundNavHistoryMapper navHistoryMapper,
                           FundPerformanceHistoryMapper performanceHistoryMapper,
                           FundStockHoldingMapper stockHoldingMapper,
                           FundFeatureDataMapper featureDataMapper,
                           FundRatingMapper ratingMapper,
                           FundValuationService valuationService,
                           IFundScoreService fundScoreService,
                           JdbcTemplate jdbcTemplate) {
        this.fundMapper = fundMapper;
        this.navHistoryMapper = navHistoryMapper;
        this.performanceHistoryMapper = performanceHistoryMapper;
        this.stockHoldingMapper = stockHoldingMapper;
        this.featureDataMapper = featureDataMapper;
        this.ratingMapper = ratingMapper;
        this.valuationService = valuationService;
        this.fundScoreService = fundScoreService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Page<CfgFund> page(String ownerUsername, long current, long size, String keyword, String fundType,
                              Boolean canBuy, boolean favoritesOnly, String sortField, String sortOrder) {
        String sortExpression = SORT_FIELDS.getOrDefault(sortField, "f.fund_code");
        String sortDirection = "descend".equalsIgnoreCase(sortOrder) ? "DESC" : "ASC";
        Page<CfgFund> page = fundMapper.selectFundPage(new Page<>(current, size),
                ownerUsername, hasText(keyword) ? keyword.trim() : null, fundType, canBuy, favoritesOnly,
                sortExpression, sortDirection);
        Map<String, com.example.crm.dto.score.FundScoreSummaryDto> scores = fundScoreService.latestSummaries(
                page.getRecords().stream().map(CfgFund::getFundCode).collect(Collectors.toList()));
        page.getRecords().forEach(fund -> {
            fund.setLatestPerformance(latestPerformance(fund.getFundCode()));
            fund.setLatestRating(latestRating(fund.getFundCode()));
            fund.setFeatures(featureRows(fund.getFundCode()));
            fund.setLatestValuation(valuationService.latest(fund.getFundCode()));
            fund.setLatestScore(scores.get(fund.getFundCode()));
        });
        return page;
    }

    @Override
    public void addFavorite(String ownerUsername, String fundCode) {
        findFund(fundCode);
        jdbcTemplate.update(
                "INSERT INTO user_fund_favorite (owner_username,fund_code) VALUES (?,?) "
                        + "ON DUPLICATE KEY UPDATE updated_at=CURRENT_TIMESTAMP",
                ownerUsername,
                fundCode);
    }

    @Override
    public void removeFavorite(String ownerUsername, String fundCode) {
        jdbcTemplate.update(
                "DELETE FROM user_fund_favorite WHERE owner_username=? AND fund_code=?",
                ownerUsername,
                fundCode);
    }

    private static Map<String, String> sortFields() {
        Map<String, String> fields = new HashMap<>();
        fields.put("fundCode", "f.fund_code"); fields.put("fundName", "f.fund_name");
        fields.put("canBuy", "f.can_buy"); fields.put("fundType", "f.fund_type");
        fields.put("fundManager", "f.fund_manager"); fields.put("managementCompany", "f.management_company");
        fields.put("netAssetScale", "CAST(f.net_asset_scale AS DECIMAL(20,4))"); fields.put("inceptionDate", "f.inception_date");
        fields.put("zhaoshangRating", "r.zhaoshang_rating"); fields.put("morningStarRating", "r.morning_star_rating");
        fields.put("weeklyReturnRate", "p.weekly_return_rate"); fields.put("monthlyReturnRate", "p.monthly_return_rate");
        fields.put("threeMonthReturnRate", "p.three_month_return_rate"); fields.put("sixMonthReturnRate", "p.six_month_return_rate");
        fields.put("oneYearReturnRate", "p.one_year_return_rate"); fields.put("twoYearReturnRate", "p.two_year_return_rate");
        fields.put("threeYearReturnRate", "p.three_year_return_rate"); fields.put("yearToDateReturnRate", "p.year_to_date_return_rate");
        fields.put("sinceInceptionReturnRate", "p.since_inception_return_rate"); fields.put("customReturnRate", "p.custom_return_rate");
        fields.put("originalFeeRate", "p.original_fee_rate"); fields.put("discountedFeeRate", "p.discounted_fee_rate");
        fields.put("cashManagementFeeRate", "p.cash_management_fee_rate");
        fields.put("standardDeviation", "(SELECT ff.standard_deviation FROM fund_feature_data ff WHERE ff.fund_code=f.fund_code AND ff.period_label='近3年' ORDER BY ff.cutoff_date DESC LIMIT 1)");
        fields.put("sharpeRatio", "(SELECT ff.sharpe_ratio FROM fund_feature_data ff WHERE ff.fund_code=f.fund_code AND ff.period_label='近3年' ORDER BY ff.cutoff_date DESC LIMIT 1)");
        fields.put("fundScore", "sr.total_score");
        fields.put("profitProbability", "sr.profit_probability");
        return java.util.Collections.unmodifiableMap(fields);
    }

    @Override
    public FundDetailResponse detail(String fundCode) {
        CfgFund fund = findFund(fundCode);
        FundDetailResponse response = new FundDetailResponse();
        response.setFund(fund);
        response.setLatestNav(latestNav(fundCode));
        response.setLatestPerformance(latestPerformance(fundCode));
        response.setLatestValuation(valuationService.latest(fundCode));
        response.setLatestHoldings(latestHoldings(fundCode));
        response.setFeatures(features(fundCode));
        response.setRatings(ratings(fundCode));
        response.setScoreDetail(fundScoreService.detail(fundCode));
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
    @Transactional
    public void delete(String fundCode) {
        findFund(fundCode);
        jdbcTemplate.update("DELETE FROM user_fund_favorite WHERE fund_code=?", fundCode);
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
                .orderByDesc(FundStockHolding::getCutoffDate)
                .orderByDesc(FundStockHolding::getReportDate)
                .orderByAsc(FundStockHolding::getRankNo);
        Page<FundStockHolding> page = stockHoldingMapper.selectPage(new Page<>(current, size), query);
        enrichLatestStockQuotes(page.getRecords());
        return page;
    }

    @Override
    public Page<FundDailyValuationDto> valuations(String fundCode, long current, long size) {
        findFund(fundCode);
        return valuationService.history(fundCode, current, size);
    }

    @Override
    public List<FundFeatureData> features(String fundCode) {
        findFund(fundCode);
        return featureRows(fundCode);
    }

    private List<FundFeatureData> featureRows(String fundCode) {
        return featureDataMapper.selectList(new LambdaQueryWrapper<FundFeatureData>()
                .eq(FundFeatureData::getFundCode, fundCode)
                .orderByDesc(FundFeatureData::getCutoffDate)
                .orderByAsc(FundFeatureData::getPeriodLabel));
    }

    @Override
    public List<FundRating> ratings(String fundCode) {
        findFund(fundCode);
        return ratingMapper.selectPage(new Page<>(1, DEFAULT_DETAIL_RATING_SIZE), new LambdaQueryWrapper<FundRating>()
                .eq(FundRating::getFundCode, fundCode)
                .orderByDesc(FundRating::getRatingDate)).getRecords();
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

    private FundPerformanceHistory latestPerformance(String fundCode) {
        return performanceHistoryMapper.selectOne(new LambdaQueryWrapper<FundPerformanceHistory>()
                .eq(FundPerformanceHistory::getFundCode, fundCode)
                .orderByDesc(FundPerformanceHistory::getNavDate)
                .last("limit 1"));
    }

    private FundRating latestRating(String fundCode) {
        return ratingMapper.selectOne(new LambdaQueryWrapper<FundRating>()
                .eq(FundRating::getFundCode, fundCode)
                .orderByDesc(FundRating::getRatingDate)
                .last("limit 1"));
    }

    private List<FundStockHolding> latestHoldings(String fundCode) {
        FundStockHolding latest = stockHoldingMapper.selectOne(new LambdaQueryWrapper<FundStockHolding>()
                .eq(FundStockHolding::getFundCode, fundCode)
                .orderByDesc(FundStockHolding::getCutoffDate)
                .orderByDesc(FundStockHolding::getReportDate)
                .last("limit 1"));
        if (latest == null || !hasText(latest.getReportDate()) || !hasText(latest.getCutoffDate())) {
            return java.util.Collections.emptyList();
        }
        List<FundStockHolding> holdings = stockHoldingMapper.selectPage(new Page<>(1, DEFAULT_DETAIL_HOLDING_SIZE), new LambdaQueryWrapper<FundStockHolding>()
                .eq(FundStockHolding::getFundCode, fundCode)
                .eq(FundStockHolding::getReportDate, latest.getReportDate())
                .eq(FundStockHolding::getCutoffDate, latest.getCutoffDate())
                .orderByAsc(FundStockHolding::getRankNo)).getRecords();
        enrichLatestStockQuotes(holdings);
        return holdings;
    }

    private void enrichLatestStockQuotes(List<FundStockHolding> holdings) {
        if (holdings == null || holdings.isEmpty()) {
            return;
        }
        List<String> stockCodes = holdings.stream()
                .map(FundStockHolding::getStockCode)
                .filter(this::hasText)
                .distinct()
                .collect(Collectors.toList());
        if (stockCodes.isEmpty()) {
            return;
        }

        String placeholders = String.join(",", Collections.nCopies(stockCodes.size(), "?"));
        String sql = "SELECT h.stock_code,h.latest_price,h.change_rate," +
                "COALESCE(h.quote_time,h.updated_at) AS quote_time " +
                "FROM stock_daily_history h " +
                "JOIN (SELECT stock_code,MAX(trade_date) AS trade_date FROM stock_daily_history " +
                "WHERE stock_code IN (" + placeholders + ") GROUP BY stock_code) latest " +
                "ON latest.stock_code=h.stock_code AND latest.trade_date=h.trade_date";
        Map<String, LatestStockQuote> quotes = new HashMap<>();
        jdbcTemplate.query(sql, resultSet -> {
            Timestamp quoteTime = resultSet.getTimestamp("quote_time");
            quotes.put(resultSet.getString("stock_code"), new LatestStockQuote(
                    resultSet.getBigDecimal("latest_price"),
                    resultSet.getBigDecimal("change_rate"),
                    quoteTime == null ? null : quoteTime.toLocalDateTime()));
        }, stockCodes.toArray());

        holdings.forEach(holding -> {
            LatestStockQuote quote = quotes.get(holding.getStockCode());
            holding.setLatestPrice(quote == null ? null : quote.latestPrice);
            holding.setChangeRate(quote == null ? null : quote.changeRate);
            holding.setQuoteTime(quote == null ? null : quote.quoteTime);
        });
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

    private static final class LatestStockQuote {
        private final BigDecimal latestPrice;
        private final BigDecimal changeRate;
        private final LocalDateTime quoteTime;

        private LatestStockQuote(BigDecimal latestPrice, BigDecimal changeRate, LocalDateTime quoteTime) {
            this.latestPrice = latestPrice;
            this.changeRate = changeRate;
            this.quoteTime = quoteTime;
        }
    }
}
