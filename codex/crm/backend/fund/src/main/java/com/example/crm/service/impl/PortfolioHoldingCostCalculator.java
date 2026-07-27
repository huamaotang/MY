package com.example.crm.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class PortfolioHoldingCostCalculator {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final int STORAGE_SCALE = 4;
    private static final int CALCULATION_SCALE = 12;

    private PortfolioHoldingCostCalculator() {
    }

    static BigDecimal infer(BigDecimal holdingAmount,
                            BigDecimal holdingProfit,
                            BigDecimal holdingReturnRate) {
        if (holdingAmount != null && holdingProfit != null) {
            BigDecimal cost = holdingAmount.subtract(holdingProfit);
            if (isValidCost(cost)) {
                return storageValue(cost);
            }
        }

        if (holdingAmount != null && holdingReturnRate != null) {
            BigDecimal rateMultiplier = BigDecimal.ONE.add(holdingReturnRate.divide(ONE_HUNDRED));
            if (rateMultiplier.signum() > 0) {
                BigDecimal cost = holdingAmount.divide(rateMultiplier, CALCULATION_SCALE, RoundingMode.HALF_UP);
                if (isValidCost(cost)) {
                    return storageValue(cost);
                }
            }
        }

        if (holdingProfit != null && holdingReturnRate != null && holdingReturnRate.signum() != 0) {
            BigDecimal rate = holdingReturnRate.divide(ONE_HUNDRED);
            BigDecimal cost = holdingProfit.divide(rate, CALCULATION_SCALE, RoundingMode.HALF_UP);
            if (isValidCost(cost)) {
                return storageValue(cost);
            }
        }
        return null;
    }

    static BigDecimal inferCostNav(BigDecimal holdingAmount,
                                   BigDecimal holdingProfit,
                                   BigDecimal holdingReturnRate,
                                   BigDecimal holdingShares,
                                   BigDecimal holdingCost) {
        BigDecimal totalCost = holdingCost;
        if (totalCost == null) {
            totalCost = infer(holdingAmount, holdingProfit, holdingReturnRate);
        }
        if (!isValidCost(totalCost) || holdingShares == null || holdingShares.signum() <= 0) {
            return null;
        }
        BigDecimal costNav = totalCost.divide(holdingShares, CALCULATION_SCALE, RoundingMode.HALF_UP);
        if (!isValidCost(costNav)) {
            return null;
        }
        return storageValue(costNav);
    }

    private static boolean isValidCost(BigDecimal value) {
        return value != null && value.signum() >= 0;
    }

    private static BigDecimal storageValue(BigDecimal value) {
        return value.setScale(STORAGE_SCALE, RoundingMode.HALF_UP);
    }
}
