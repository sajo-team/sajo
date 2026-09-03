package com.sajo.market_service.strategy.controller.dto.request;

import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record StrategyUpdateRequest(
        String strategyName,

        @Positive
        Long buyConditionPrice,

        @Positive
        Long sellConditionPrice,

        @Positive
        BigDecimal stopLossRate,

        @Positive
        BigDecimal targetReturnRate,

        @Positive
        Long allocatedAmount,

        @Positive
        BigDecimal perCondition,

        @Positive
        BigDecimal pbrCondition,

        @Positive
        BigDecimal roeCondition
) {
}
