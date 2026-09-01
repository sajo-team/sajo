package com.sajo.trading_service.trading.controller.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record TradingLimitCreateRequest(

        @NotNull
        @Positive
        Long dailyMaxOrderAmount,

        @NotNull
        @Positive
        Integer dailyMaxOrderCount,

        @NotNull
        @Positive
        BigDecimal dailyLossLimitRate
) {
}
