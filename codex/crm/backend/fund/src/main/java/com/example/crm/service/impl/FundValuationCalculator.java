package com.example.crm.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

final class FundValuationCalculator {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final int RESULT_SCALE = 4;

    private FundValuationCalculator() {
    }

    static Result calculate(List<Component> components) {
        BigDecimal holdingWeight = BigDecimal.ZERO;
        BigDecimal quotedHoldingWeight = BigDecimal.ZERO;
        BigDecimal weightedChange = BigDecimal.ZERO;
        int holdingCount = 0;
        int quotedHoldingCount = 0;

        if (components != null) {
            for (Component component : components) {
                if (component == null || component.getNetValueRatio() == null) {
                    continue;
                }
                BigDecimal weight = component.getNetValueRatio();
                holdingWeight = holdingWeight.add(weight);
                holdingCount++;
                if (component.getChangeRate() == null) {
                    continue;
                }
                quotedHoldingWeight = quotedHoldingWeight.add(weight);
                weightedChange = weightedChange.add(weight.multiply(component.getChangeRate()));
                quotedHoldingCount++;
            }
        }

        BigDecimal estimatedChangeRate = quotedHoldingCount == 0
                ? null
                : weightedChange.divide(ONE_HUNDRED, RESULT_SCALE, RoundingMode.HALF_UP);
        BigDecimal coverageRate = holdingWeight.signum() == 0
                ? null
                : quotedHoldingWeight.multiply(ONE_HUNDRED)
                        .divide(holdingWeight, RESULT_SCALE, RoundingMode.HALF_UP);
        return new Result(
                scale(estimatedChangeRate),
                scale(holdingWeight),
                scale(quotedHoldingWeight),
                scale(coverageRate),
                holdingCount,
                quotedHoldingCount);
    }

    static BigDecimal estimatedUnitNav(BigDecimal baseUnitNav, BigDecimal estimatedChangeRate) {
        if (baseUnitNav == null || estimatedChangeRate == null) {
            return null;
        }
        return baseUnitNav.multiply(
                        BigDecimal.ONE.add(estimatedChangeRate.divide(ONE_HUNDRED, 10, RoundingMode.HALF_UP)))
                .setScale(6, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(RESULT_SCALE, RoundingMode.HALF_UP);
    }

    static final class Component {
        private final BigDecimal netValueRatio;
        private final BigDecimal changeRate;

        Component(BigDecimal netValueRatio, BigDecimal changeRate) {
            this.netValueRatio = netValueRatio;
            this.changeRate = changeRate;
        }

        BigDecimal getNetValueRatio() {
            return netValueRatio;
        }

        BigDecimal getChangeRate() {
            return changeRate;
        }
    }

    static final class Result {
        private final BigDecimal estimatedChangeRate;
        private final BigDecimal holdingWeight;
        private final BigDecimal quotedHoldingWeight;
        private final BigDecimal quoteCoverageRate;
        private final int holdingCount;
        private final int quotedHoldingCount;

        Result(BigDecimal estimatedChangeRate,
               BigDecimal holdingWeight,
               BigDecimal quotedHoldingWeight,
               BigDecimal quoteCoverageRate,
               int holdingCount,
               int quotedHoldingCount) {
            this.estimatedChangeRate = estimatedChangeRate;
            this.holdingWeight = holdingWeight;
            this.quotedHoldingWeight = quotedHoldingWeight;
            this.quoteCoverageRate = quoteCoverageRate;
            this.holdingCount = holdingCount;
            this.quotedHoldingCount = quotedHoldingCount;
        }

        BigDecimal getEstimatedChangeRate() {
            return estimatedChangeRate;
        }

        BigDecimal getHoldingWeight() {
            return holdingWeight;
        }

        BigDecimal getQuotedHoldingWeight() {
            return quotedHoldingWeight;
        }

        BigDecimal getQuoteCoverageRate() {
            return quoteCoverageRate;
        }

        int getHoldingCount() {
            return holdingCount;
        }

        int getQuotedHoldingCount() {
            return quotedHoldingCount;
        }
    }
}
