package com.example.crm.service.impl;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PortfolioHoldingCostCalculatorTest {

    @Test
    void usesAmountAndProfitAsTheMostAccurateCostBasis() {
        BigDecimal result = PortfolioHoldingCostCalculator.infer(
                new BigDecimal("9898.16"),
                new BigDecimal("2717.77"),
                new BigDecimal("37.85"));

        assertEquals(new BigDecimal("7180.3900"), result);
    }

    @Test
    void supportsNegativeHoldingProfit() {
        BigDecimal result = PortfolioHoldingCostCalculator.infer(
                new BigDecimal("22195.22"),
                new BigDecimal("-1414.78"),
                new BigDecimal("-5.99"));

        assertEquals(new BigDecimal("23610.0000"), result);
    }

    @Test
    void fallsBackToAmountAndReturnRate() {
        BigDecimal result = PortfolioHoldingCostCalculator.infer(
                new BigDecimal("9898.16"),
                null,
                new BigDecimal("37.85"));

        assertEquals(new BigDecimal("7180.3845"), result);
    }

    @Test
    void infersNetValueCostFromTheCostBasisAndShares() {
        BigDecimal result = PortfolioHoldingCostCalculator.inferCostNav(
                new BigDecimal("9898.16"),
                new BigDecimal("2717.77"),
                new BigDecimal("37.85"),
                new BigDecimal("1000"),
                null);

        assertEquals(new BigDecimal("7.1804"), result);
    }

    @Test
    void infersNetValueCostFromAProvidedCostBasis() {
        BigDecimal result = PortfolioHoldingCostCalculator.inferCostNav(
                null,
                null,
                null,
                new BigDecimal("2000"),
                new BigDecimal("23610"));

        assertEquals(new BigDecimal("11.8050"), result);
    }

    @Test
    void returnsNullWhenKnownValuesCannotProduceAValidCost() {
        assertNull(PortfolioHoldingCostCalculator.infer(null, null, null));
        assertNull(PortfolioHoldingCostCalculator.inferCostNav(null, null, null, null, null));
        assertNull(PortfolioHoldingCostCalculator.inferCostNav(null, null, null, BigDecimal.ZERO, BigDecimal.ONE));
        assertNull(PortfolioHoldingCostCalculator.infer(BigDecimal.ONE, null, new BigDecimal("-100")));
    }
}
