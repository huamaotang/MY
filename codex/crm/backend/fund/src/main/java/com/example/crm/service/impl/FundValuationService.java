package com.example.crm.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.crm.dto.FundDailyValuationDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class FundValuationService {
    private static final DateTimeFormatter COMPACT_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final JdbcTemplate jdbcTemplate;

    public FundValuationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public FundDailyValuationDto latest(String fundCode) {
        LocalDate valuationDate = jdbcTemplate.query(
                "SELECT MAX(trade_date) FROM stock_daily_history",
                resultSet -> {
                    if (!resultSet.next()) {
                        return null;
                    }
                    Date value = resultSet.getDate(1);
                    return value == null ? null : value.toLocalDate();
                });
        return valuationDate == null ? null : valuation(fundCode, valuationDate);
    }

    public Page<FundDailyValuationDto> history(String fundCode, long current, long size) {
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 200);
        String earliestReportDate = reportDate(fundCode, null, false);
        if (earliestReportDate == null) {
            return new Page<>(safeCurrent, safeSize, 0);
        }

        LocalDate earliestDate = LocalDate.parse(earliestReportDate, COMPACT_DATE);
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT trade_date) FROM stock_daily_history WHERE trade_date>=?",
                Long.class,
                Date.valueOf(earliestDate));
        List<LocalDate> valuationDates = jdbcTemplate.query(
                "SELECT DISTINCT trade_date FROM stock_daily_history WHERE trade_date>=? " +
                        "ORDER BY trade_date DESC LIMIT ? OFFSET ?",
                (resultSet, rowNum) -> resultSet.getDate("trade_date").toLocalDate(),
                Date.valueOf(earliestDate),
                safeSize,
                (safeCurrent - 1) * safeSize);

        List<FundDailyValuationDto> records = new ArrayList<>();
        for (LocalDate valuationDate : valuationDates) {
            FundDailyValuationDto valuation = valuation(fundCode, valuationDate);
            if (valuation != null) {
                records.add(valuation);
            }
        }
        Page<FundDailyValuationDto> page = new Page<>(safeCurrent, safeSize, total == null ? 0 : total);
        page.setRecords(records);
        return page;
    }

    private FundDailyValuationDto valuation(String fundCode, LocalDate valuationDate) {
        String holdingReportDate = reportDate(fundCode, valuationDate, true);
        if (holdingReportDate == null) {
            return null;
        }

        List<FundValuationCalculator.Component> components = new ArrayList<>();
        final LocalDateTime[] quoteUpdatedAt = new LocalDateTime[1];
        jdbcTemplate.query(
                "SELECT f.net_value_ratio,s.change_rate,s.updated_at " +
                        "FROM fund_stock_holding f " +
                        "LEFT JOIN stock_daily_history s ON s.stock_code=f.stock_code AND s.trade_date=? " +
                        "WHERE f.fund_code=? AND f.report_date=? ORDER BY f.rank_no ASC,f.stock_code ASC",
                resultSet -> {
                    BigDecimal weight = resultSet.getBigDecimal("net_value_ratio");
                    BigDecimal changeRate = resultSet.getBigDecimal("change_rate");
                    components.add(new FundValuationCalculator.Component(weight, changeRate));
                    if (changeRate != null && resultSet.getTimestamp("updated_at") != null) {
                        LocalDateTime updatedAt = resultSet.getTimestamp("updated_at").toLocalDateTime();
                        if (quoteUpdatedAt[0] == null || updatedAt.isAfter(quoteUpdatedAt[0])) {
                            quoteUpdatedAt[0] = updatedAt;
                        }
                    }
                },
                Date.valueOf(valuationDate),
                fundCode,
                holdingReportDate);
        if (components.isEmpty()) {
            return null;
        }

        FundValuationCalculator.Result calculated = FundValuationCalculator.calculate(components);
        BaseNav baseNav = baseNav(fundCode, valuationDate);
        FundDailyValuationDto dto = new FundDailyValuationDto();
        dto.setFundCode(fundCode);
        dto.setValuationDate(valuationDate);
        dto.setHoldingReportDate(holdingReportDate);
        dto.setBaseNavDate(baseNav == null ? null : baseNav.navDate);
        dto.setBaseUnitNav(baseNav == null ? null : baseNav.unitNav);
        dto.setEstimatedChangeRate(calculated.getEstimatedChangeRate());
        dto.setEstimatedUnitNav(FundValuationCalculator.estimatedUnitNav(
                dto.getBaseUnitNav(), dto.getEstimatedChangeRate()));
        dto.setHoldingWeight(calculated.getHoldingWeight());
        dto.setQuotedHoldingWeight(calculated.getQuotedHoldingWeight());
        dto.setQuoteCoverageRate(calculated.getQuoteCoverageRate());
        dto.setHoldingCount(calculated.getHoldingCount());
        dto.setQuotedHoldingCount(calculated.getQuotedHoldingCount());
        dto.setQuoteUpdatedAt(quoteUpdatedAt[0]);
        return dto;
    }

    private String reportDate(String fundCode, LocalDate valuationDate, boolean latest) {
        String function = latest ? "MAX" : "MIN";
        String sql = "SELECT " + function + "(report_date) FROM fund_stock_holding WHERE fund_code=?";
        List<Object> args = new ArrayList<>();
        args.add(fundCode);
        if (valuationDate != null) {
            sql += " AND report_date<=?";
            args.add(valuationDate.format(COMPACT_DATE));
        }
        return jdbcTemplate.query(sql, resultSet -> {
            if (!resultSet.next()) {
                return null;
            }
            return resultSet.getString(1);
        }, args.toArray());
    }

    private BaseNav baseNav(String fundCode, LocalDate valuationDate) {
        List<BaseNav> rows = jdbcTemplate.query(
                "SELECT nav_date,unit_nav FROM fund_nav_history " +
                        "WHERE fund_code=? AND nav_date<? AND unit_nav IS NOT NULL " +
                        "ORDER BY nav_date DESC LIMIT 1",
                (resultSet, rowNum) -> new BaseNav(
                        resultSet.getString("nav_date"),
                        resultSet.getBigDecimal("unit_nav")),
                fundCode,
                valuationDate.format(COMPACT_DATE));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static final class BaseNav {
        private final String navDate;
        private final BigDecimal unitNav;

        private BaseNav(String navDate, BigDecimal unitNav) {
            this.navDate = navDate;
            this.unitNav = unitNav;
        }
    }
}
