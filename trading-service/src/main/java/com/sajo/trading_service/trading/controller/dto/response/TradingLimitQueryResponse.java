package com.sajo.trading_service.trading.controller.dto.response;

import com.sajo.trading_service.trading.domain.TradingLimit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TradingLimitQueryResponse(
        UUID tradingLimitId,
        Long dailyMaxOrderAmount,
        Integer dailyMaxOrderCount,
        BigDecimal dailyLossLimitRate,
        Instant createdAt,
        Instant updatedAt
) {
    public static TradingLimitQueryResponse from(TradingLimit tradingLimit) {
        return new TradingLimitQueryResponse(
                tradingLimit.getId(),
                tradingLimit.getDailyMaxOrderAmount(),
                tradingLimit.getDailyMaxOrderCount(),
                tradingLimit.getDailyLossLimitRate(),
                tradingLimit.getCreatedAt(),
                tradingLimit.getUpdatedAt()
        );
    }
}
