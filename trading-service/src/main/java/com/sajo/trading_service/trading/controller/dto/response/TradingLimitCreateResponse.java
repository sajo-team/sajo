package com.sajo.trading_service.trading.controller.dto.response;

import com.sajo.trading_service.trading.domain.TradingLimit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TradingLimitCreateResponse(
        UUID tradingLimitId,
        Long dailyMaxOrderAmount,
        Integer dailyMaxOrderCount,
        BigDecimal dailyLossLimitRate,
        Instant createdAt
) {
    public static TradingLimitCreateResponse from(TradingLimit tradingLimit) {
        return new TradingLimitCreateResponse(
                tradingLimit.getId(),
                tradingLimit.getDailyMaxOrderAmount(),
                tradingLimit.getDailyMaxOrderCount(),
                tradingLimit.getDailyLossLimitRate(),
                tradingLimit.getCreatedAt()
        );
    }
}
