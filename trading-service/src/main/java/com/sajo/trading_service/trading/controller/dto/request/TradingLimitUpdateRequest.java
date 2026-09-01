package com.sajo.trading_service.trading.controller.dto.request;

import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record TradingLimitUpdateRequest(
        @Positive
        Long dailyMaxOrderAmount,

        @Positive
        Integer dailyMaxOrderCount,

        @Positive
        BigDecimal dailyLossLimitRate
) {
}
