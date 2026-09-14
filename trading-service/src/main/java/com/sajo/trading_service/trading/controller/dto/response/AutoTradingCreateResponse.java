package com.sajo.trading_service.trading.controller.dto.response;

import com.sajo.trading_service.trading.domain.AutoTrading;
import com.sajo.trading_service.trading.domain.enums.AutoTradingDirection;

import java.time.Instant;
import java.util.UUID;

public record AutoTradingCreateResponse(
        UUID autoTradingId,
        UUID strategyId,
        AutoTradingDirection direction,
        Boolean enabled,
        Instant createdAt
) {
    public static AutoTradingCreateResponse from(
            AutoTrading autoTrading
    ){
        return new AutoTradingCreateResponse(
                autoTrading.getId(),
                autoTrading.getStrategyId(),
                autoTrading.getDirection(),
                autoTrading.getEnabled(),
                autoTrading.getCreatedAt()
        );
    }
}
