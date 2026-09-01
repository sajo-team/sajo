package com.sajo.trading_service.trading.controller.dto.response;

import com.sajo.trading_service.trading.domain.AutoTrading;

import java.time.Instant;
import java.util.UUID;

public record AutoTradingQueryResponse(
        UUID autoTradingId,
        UUID strategyId,
        Boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
    public static AutoTradingQueryResponse from(
            AutoTrading autoTrading
    ){
        return new AutoTradingQueryResponse(
                autoTrading.getId(),
                autoTrading.getStrategyId(),
                autoTrading.getEnabled(),
                autoTrading.getCreatedAt(),
                autoTrading.getUpdatedAt()
        );
    }
}
