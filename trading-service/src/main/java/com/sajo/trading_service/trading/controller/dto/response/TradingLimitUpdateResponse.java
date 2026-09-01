package com.sajo.trading_service.trading.controller.dto.response;

import com.sajo.trading_service.trading.domain.TradingLimit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TradingLimitUpdateResponse(
        UUID tradingLimitId,
        Long dailyMaxOrderAmount,
        Integer dailyMaxOrderCount,
        BigDecimal dailyLossLimitRate,
        Instant updatedAt
) {
    public static TradingLimitUpdateResponse from(
            TradingLimit tradingLimit
    ){
        return new TradingLimitUpdateResponse(
                tradingLimit.getId(),
                tradingLimit.getDailyMaxOrderAmount(),
                tradingLimit.getDailyMaxOrderCount(),
                tradingLimit.getDailyLossLimitRate(),
                tradingLimit.getUpdatedAt()
        );
    }
}
