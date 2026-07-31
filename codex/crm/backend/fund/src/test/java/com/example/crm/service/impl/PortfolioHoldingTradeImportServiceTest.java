package com.example.crm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.crm.dto.portfolio.PortfolioHoldingConfirmItemRequest;
import com.example.crm.dto.portfolio.PortfolioHoldingConfirmRequest;
import com.example.crm.dto.portfolio.PortfolioHoldingConfirmResponse;
import com.example.crm.entity.CfgFund;
import com.example.crm.entity.FundHoldingImportBatch;
import com.example.crm.entity.FundHoldingTradeImportItem;
import com.example.crm.entity.UserFundHolding;
import com.example.crm.mapper.CfgFundMapper;
import com.example.crm.mapper.FundHoldingImportItemMapper;
import com.example.crm.mapper.FundHoldingImportMapper;
import com.example.crm.mapper.FundHoldingTradeImportItemMapper;
import com.example.crm.mapper.UserFundHoldingMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortfolioHoldingTradeImportServiceTest {
    private FundHoldingImportMapper importMapper;
    private FundHoldingTradeImportItemMapper tradeItemMapper;
    private UserFundHoldingMapper holdingMapper;
    private CfgFundMapper fundMapper;
    private PortfolioHoldingServiceImpl service;
    private FundHoldingImportBatch batch;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                UserFundHolding.class);
        importMapper = mock(FundHoldingImportMapper.class);
        FundHoldingImportItemMapper itemMapper = mock(FundHoldingImportItemMapper.class);
        tradeItemMapper = mock(FundHoldingTradeImportItemMapper.class);
        holdingMapper = mock(UserFundHoldingMapper.class);
        fundMapper = mock(CfgFundMapper.class);
        FundValuationService valuationService = mock(FundValuationService.class);
        service = new PortfolioHoldingServiceImpl(
                importMapper,
                itemMapper,
                tradeItemMapper,
                holdingMapper,
                fundMapper,
                new ObjectMapper(),
                valuationService,
                "fund_spider/tools/portfolio_holding_ocr.py",
                "python3");
        batch = new FundHoldingImportBatch();
        batch.setId(10L);
        batch.setOwnerUsername("alice");
        batch.setSourceLabel("alipay");
        batch.setImportType("trade");
        batch.setStatus("PREVIEWED");
        batch.setWarningsJson("[]");
        when(importMapper.selectOne(any())).thenReturn(batch);
        when(tradeItemMapper.selectCount(any())).thenReturn(0L);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void holdingSnapshotOverwriteIsScopedToOwnerAndSource() {
        batch.setImportType("holding");
        CfgFund fund = new CfgFund();
        fund.setFundCode("000001");
        fund.setFundName("测试基金A");
        when(fundMapper.selectOne(any())).thenReturn(fund);
        when(holdingMapper.selectOne(any())).thenReturn(null);

        PortfolioHoldingConfirmItemRequest item = new PortfolioHoldingConfirmItemRequest();
        item.setRowNo(1);
        item.setFundCode("000001");
        item.setHoldingAmount(new BigDecimal("123.45"));
        PortfolioHoldingConfirmRequest request = new PortfolioHoldingConfirmRequest();
        request.setScreenshotDate(LocalDate.of(2026, 7, 30));
        request.setItems(Collections.singletonList(item));

        PortfolioHoldingConfirmResponse response = service.confirm("alice", 10L, request);

        ArgumentCaptor<UserFundHolding> inserted =
                ArgumentCaptor.forClass(UserFundHolding.class);
        verify(holdingMapper).insert(inserted.capture());
        assertEquals("alice", inserted.getValue().getOwnerUsername());
        assertEquals("alipay", inserted.getValue().getSourceLabel());
        assertEquals(1, response.getAffectedHoldingCount());

        ArgumentCaptor<LambdaQueryWrapper> deleteQuery =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(holdingMapper).delete(deleteQuery.capture());
        String deleteSql = deleteQuery.getValue().getSqlSegment();
        assertTrue(deleteSql.contains("owner_username"));
        assertTrue(deleteSql.contains("source_label"));
        assertTrue(deleteQuery.getValue().getParamNameValuePairs()
                .containsValue("alice"));
        assertTrue(deleteQuery.getValue().getParamNameValuePairs()
                .containsValue("alipay"));
    }

    @Test
    void appliesNetAmountAndPreservesSnapshotMetrics() {
        UserFundHolding holding = holding("1000", LocalDate.of(2026, 7, 20));
        FundHoldingTradeImportItem buy = trade(1L, "BUY", "300", "2026-07-21T10:00:00");
        FundHoldingTradeImportItem sell = trade(2L, "SELL", "100", "2026-07-22T10:00:00");
        when(tradeItemMapper.selectList(any())).thenReturn(Arrays.asList(buy, sell));
        when(holdingMapper.selectOne(any())).thenReturn(holding);

        PortfolioHoldingConfirmResponse response = service.confirm("alice", 10L, null);

        ArgumentCaptor<UserFundHolding> captor = ArgumentCaptor.forClass(UserFundHolding.class);
        verify(holdingMapper).updateById(captor.capture());
        UserFundHolding updated = captor.getValue();
        assertEquals(new BigDecimal("1200"), updated.getHoldingAmount());
        assertEquals(new BigDecimal("88"), updated.getHoldingProfit());
        assertEquals(new BigDecimal("900"), updated.getHoldingCost());
        assertEquals(new BigDecimal("700"), updated.getHoldingShares());
        assertEquals(new BigDecimal("1.2345"), updated.getCostNav());
        assertEquals(LocalDate.of(2026, 7, 20), updated.getScreenshotDate());
        assertEquals(1, response.getAffectedHoldingCount());
        assertEquals(2, response.getAppliedTransactionCount());
        verify(holdingMapper, never()).insert(any());
        verify(holdingMapper, never()).delete(any());
    }

    @Test
    void skipsTransactionAtOrBeforeSnapshotBaseline() {
        UserFundHolding holding = holding("1000", LocalDate.of(2026, 7, 20));
        FundHoldingTradeImportItem old = trade(1L, "BUY", "300", "2026-07-20T23:59:59");
        when(tradeItemMapper.selectList(any())).thenReturn(Collections.singletonList(old));
        when(holdingMapper.selectOne(any())).thenReturn(holding);

        PortfolioHoldingConfirmResponse response = service.confirm("alice", 10L, null);

        verify(holdingMapper, never()).updateById(any());
        assertEquals("SKIPPED_BASELINE", old.getStatus());
        assertEquals(0, response.getAppliedTransactionCount());
    }

    @Test
    void skipsAlreadyAppliedFingerprint() {
        UserFundHolding holding = holding("1000", null);
        FundHoldingTradeImportItem duplicate = trade(
                1L, "BUY", "300", "2026-07-21T10:00:00");
        when(tradeItemMapper.selectList(any())).thenReturn(Collections.singletonList(duplicate));
        when(holdingMapper.selectOne(any())).thenReturn(holding);
        when(tradeItemMapper.selectCount(any())).thenReturn(1L);

        PortfolioHoldingConfirmResponse response = service.confirm("alice", 10L, null);

        verify(holdingMapper, never()).updateById(any());
        assertEquals("SKIPPED_DUPLICATE", duplicate.getStatus());
        assertEquals(0, response.getAppliedTransactionCount());
    }

    @Test
    void missingHoldingNeverCreatesOrDeletes() {
        FundHoldingTradeImportItem buy = trade(
                1L, "BUY", "300", "2026-07-21T10:00:00");
        when(tradeItemMapper.selectList(any())).thenReturn(Collections.singletonList(buy));
        when(holdingMapper.selectOne(any())).thenReturn(null);

        PortfolioHoldingConfirmResponse response = service.confirm("alice", 10L, null);

        verify(holdingMapper, never()).insert(any());
        verify(holdingMapper, never()).updateById(any());
        verify(holdingMapper, never()).delete(any());
        assertEquals("SKIPPED_MISSING_HOLDING", buy.getStatus());
        assertEquals(0, response.getAffectedHoldingCount());
    }

    @Test
    void oversizedSellClampsToZeroAndKeepsHolding() {
        UserFundHolding holding = holding("100", null);
        FundHoldingTradeImportItem sell = trade(
                1L, "SELL", "150", "2026-07-21T10:00:00");
        when(tradeItemMapper.selectList(any())).thenReturn(Collections.singletonList(sell));
        when(holdingMapper.selectOne(any())).thenReturn(holding);

        PortfolioHoldingConfirmResponse response = service.confirm("alice", 10L, null);

        assertEquals(BigDecimal.ZERO, holding.getHoldingAmount());
        assertEquals("APPLIED_CLAMPED", sell.getStatus());
        assertEquals(BigDecimal.ZERO, sell.getAfterHoldingAmount());
        assertNull(holding.getTodayProfit());
        verify(holdingMapper, never()).delete(any());
        assertEquals(1, response.getAppliedTransactionCount());
    }

    private UserFundHolding holding(String amount, LocalDate screenshotDate) {
        UserFundHolding holding = new UserFundHolding();
        holding.setId(99L);
        holding.setOwnerUsername("alice");
        holding.setSourceLabel("alipay");
        holding.setFundCode("000001");
        holding.setFundName("测试基金A");
        holding.setHoldingAmount(new BigDecimal(amount));
        holding.setHoldingProfit(new BigDecimal("88"));
        holding.setHoldingReturnRate(new BigDecimal("9.7778"));
        holding.setHoldingCost(new BigDecimal("900"));
        holding.setHoldingShares(new BigDecimal("700"));
        holding.setCostNav(new BigDecimal("1.2345"));
        holding.setScreenshotDate(screenshotDate);
        return holding;
    }

    private FundHoldingTradeImportItem trade(
            Long id, String operation, String amount, String timestamp) {
        FundHoldingTradeImportItem item = new FundHoldingTradeImportItem();
        item.setId(id);
        item.setImportId(10L);
        item.setRowNo(id.intValue());
        item.setGroupKey("group");
        item.setFundCode("000001");
        item.setFundName("测试基金A");
        item.setOperationType(operation);
        item.setTransactionAmount(new BigDecimal(amount));
        item.setTransactionAt(LocalDateTime.parse(timestamp));
        item.setTransactionStatus("SUCCESS");
        item.setFingerprint("fingerprint-" + id);
        item.setStatus("APPLICABLE");
        return item;
    }
}
