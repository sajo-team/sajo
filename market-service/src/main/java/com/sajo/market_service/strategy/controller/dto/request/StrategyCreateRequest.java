package com.sajo.market_service.strategy.controller.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record StrategyCreateRequest(
        @NotNull
        UUID stockId,

        @NotBlank
        String stockCode,

        @NotBlank
        String strategyName,

        @NotNull
        @Positive
        Long buyConditionPrice,

        @NotNull
        @Positive
        Long sellConditionPrice,

        @NotNull
        @Positive
        BigDecimal stopLossRate,

        @Positive
        BigDecimal targetReturnRate,

        @NotNull
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
