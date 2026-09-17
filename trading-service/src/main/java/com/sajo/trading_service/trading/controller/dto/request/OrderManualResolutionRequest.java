package com.sajo.trading_service.trading.controller.dto.request;

import com.sajo.trading_service.trading.domain.enums.OrderManualResolution;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record OrderManualResolutionRequest(

        @NotNull
        OrderManualResolution resolution,

        String brokerOrderNo,

        @PositiveOrZero
        Integer totalFilledQuantity,

        @Positive
        BigDecimal averageExecutionPrice,

        @Positive
        Long totalExecutionAmount,

        String reason
) {
}