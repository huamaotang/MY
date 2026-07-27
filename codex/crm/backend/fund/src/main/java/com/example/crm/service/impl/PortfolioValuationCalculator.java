package com.example.crm.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class PortfolioValuationCalculator {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private PortfolioValuationCalculator() {
    }

    static BigDecimal estimatedDailyProfit(BigDecimal holdingAmount,
                                           BigDecimal holdingShares,
                                           BigDecimal baseUnitNav,
                                           BigDecimal estimatedChangeRate) {
        if (estimatedChangeRate == null) {
            return null;
        }
        BigDecimal valuationBase = holdingAmount;
        if (valuationBase == null && holdingShares != null && baseUnitNav != null) {
            valuationBase = holdingShares.multiply(baseUnitNav);
        }
        if (valuationBase == null) {
            return null;
        }
        return valuationBase.multiply(estimatedChangeRate)
                .divide(ONE_HUNDRED, 4, RoundingMode.HALF_UP);
    }

    static BigDecimal estimatedHoldingAmount(BigDecimal holdingAmount, BigDecimal estimatedDailyProfit) {
        if (holdingAmount == null || estimatedDailyProfit == null) {
            return null;
        }
        return holdingAmount.add(estimatedDailyProfit).setScale(4, RoundingMode.HALF_UP);
    }

    static BigDecimal estimatedCumulativeChangeRate(BigDecimal costNav, BigDecimal estimatedUnitNav) {
        if (costNav == null || costNav.signum() <= 0 || estimatedUnitNav == null) {
            return null;
        }
        return estimatedUnitNav.subtract(costNav)
                .multiply(ONE_HUNDRED)
                .divide(costNav, 4, RoundingMode.HALF_UP);
    }

    static BigDecimal estimatedCumulativeProfit(BigDecimal holdingShares,
                                                BigDecimal costNav,
                                                BigDecimal estimatedUnitNav,
                                                BigDecimal holdingCost,
                                                BigDecimal estimatedCumulativeChangeRate) {
        if (holdingShares != null && costNav != null && estimatedUnitNav != null) {
            return holdingShares.multiply(estimatedUnitNav.subtract(costNav))
                    .setScale(4, RoundingMode.HALF_UP);
        }
        if (holdingCost == null || estimatedCumulativeChangeRate == null) {
            return null;
        }
        return holdingCost.multiply(estimatedCumulativeChangeRate)
                .divide(ONE_HUNDRED, 4, RoundingMode.HALF_UP);
    }
}
