package com.example.crm.service.impl;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FundValuationCalculatorTest {
    @Test
    void calculatesFundChangeFromNetValueWeights() {
        FundValuationCalculator.Result result = FundValuationCalculator.calculate(Arrays.asList(
                component("10", "2"),
                component("20", "-1"),
                component("5", null)));

        assertEquals(new BigDecimal("0.0000"), result.getEstimatedChangeRate());
        assertEquals(new BigDecimal("35.0000"), result.getHoldingWeight());
        assertEquals(new BigDecimal("30.0000"), result.getQuotedHoldingWeight());
        assertEquals(new BigDecimal("85.7143"), result.getQuoteCoverageRate());
        assertEquals(3, result.getHoldingCount());
        assertEquals(2, result.getQuotedHoldingCount());
    }

    @Test
    void keepsAbsoluteFundWeightInsteadOfRenormalizingTopHoldings() {
        FundValuationCalculator.Result result = FundValuationCalculator.calculate(Arrays.asList(
                component("8", "3"),
                component("12", "1")));

        assertEquals(new BigDecimal("0.3600"), result.getEstimatedChangeRate());
        assertEquals(new BigDecimal("100.0000"), result.getQuoteCoverageRate());
    }

    @Test
    void returnsNoEstimateWhenNoHoldingHasAQuote() {
        FundValuationCalculator.Result result = FundValuationCalculator.calculate(Arrays.asList(
                component("10", null),
                component("5", null)));

        assertNull(result.getEstimatedChangeRate());
        assertEquals(new BigDecimal("0.0000"), result.getQuotedHoldingWeight());
    }

    @Test
    void estimatesUnitNavFromPreviousPublishedNav() {
        assertEquals(new BigDecimal("1.238250"),
                FundValuationCalculator.estimatedUnitNav(
                        new BigDecimal("1.230000"),
                        new BigDecimal("0.6707")));
    }

    private FundValuationCalculator.Component component(String weight, String changeRate) {
        return new FundValuationCalculator.Component(
                new BigDecimal(weight),
                changeRate == null ? null : new BigDecimal(changeRate));
    }
}
