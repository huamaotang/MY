package com.example.crm.service.impl;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PortfolioValuationCalculatorTest {
    @Test
    void calculatesDailyProfitFromCurrentHoldingAmount() {
        BigDecimal profit = PortfolioValuationCalculator.estimatedDailyProfit(
                new BigDecimal("10000"),
                new BigDecimal("8000"),
                new BigDecimal("1.20"),
                new BigDecimal("0.36"));

        assertEquals(new BigDecimal("36.0000"), profit);
        assertEquals(new BigDecimal("10036.0000"),
                PortfolioValuationCalculator.estimatedHoldingAmount(new BigDecimal("10000"), profit));
    }

    @Test
    void fallsBackToSharesAndBaseNavWhenHoldingAmountIsMissing() {
        assertEquals(new BigDecimal("-12.5000"),
                PortfolioValuationCalculator.estimatedDailyProfit(
                        null,
                        new BigDecimal("1000"),
                        new BigDecimal("1.25"),
                        new BigDecimal("-1")));
    }

    @Test
    void returnsNoProfitWithoutRateOrValuationBase() {
        assertNull(PortfolioValuationCalculator.estimatedDailyProfit(
                new BigDecimal("1000"), null, null, null));
        assertNull(PortfolioValuationCalculator.estimatedDailyProfit(
                null, null, null, new BigDecimal("1")));
    }

    @Test
    void calculatesCumulativeValuationFromCostNavAndShares() {
        BigDecimal cumulativeChangeRate =
                PortfolioValuationCalculator.estimatedCumulativeChangeRate(
                        new BigDecimal("1.200000"),
                        new BigDecimal("1.238250"));

        assertEquals(new BigDecimal("3.1875"), cumulativeChangeRate);
        assertEquals(new BigDecimal("306.0000"),
                PortfolioValuationCalculator.estimatedCumulativeProfit(
                        new BigDecimal("8000"),
                        new BigDecimal("1.200000"),
                        new BigDecimal("1.238250"),
                        new BigDecimal("9600"),
                        cumulativeChangeRate));
    }

    @Test
    void fallsBackToHoldingCostForCumulativeProfit() {
        BigDecimal cumulativeChangeRate = new BigDecimal("-2.5000");

        assertEquals(new BigDecimal("-240.0000"),
                PortfolioValuationCalculator.estimatedCumulativeProfit(
                        null,
                        new BigDecimal("1.200000"),
                        new BigDecimal("1.170000"),
                        new BigDecimal("9600"),
                        cumulativeChangeRate));
    }

    @Test
    void rejectsMissingOrNonPositiveCostNavForCumulativeRate() {
        assertNull(PortfolioValuationCalculator.estimatedCumulativeChangeRate(
                null, new BigDecimal("1.20")));
        assertNull(PortfolioValuationCalculator.estimatedCumulativeChangeRate(
                BigDecimal.ZERO, new BigDecimal("1.20")));
    }
}
