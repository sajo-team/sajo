package com.sajo.trading_service.trading.controller.dto.request;

import com.sajo.trading_service.trading.domain.enums.OrderManualResolution;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record OrderManualResolutionRequest(

        @NotNull
        OrderManualResolution resolution,

        String brokerOrderNo,

        Integer totalFilledQuantity,

        BigDecimal averageExecutionPrice,

        Long totalExecutionAmount,

        String reason
) {
}