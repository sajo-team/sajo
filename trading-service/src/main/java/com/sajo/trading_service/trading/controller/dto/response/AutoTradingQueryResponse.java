package com.sajo.trading_service.trading.controller.dto.response;

import com.sajo.trading_service.trading.domain.AutoTrading;
import com.sajo.trading_service.trading.domain.enums.AutoTradingDirection;

import java.time.Instant;
import java.util.UUID;

public record AutoTradingQueryResponse(
        UUID autoTradingId,
        UUID strategyId,
        AutoTradingDirection direction,
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
                autoTrading.getDirection(),
                autoTrading.getEnabled(),
                autoTrading.getCreatedAt(),
                autoTrading.getUpdatedAt()
        );
    }
}
