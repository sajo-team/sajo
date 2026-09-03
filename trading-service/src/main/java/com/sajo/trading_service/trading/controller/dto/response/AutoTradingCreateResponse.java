package com.sajo.trading_service.trading.controller.dto.response;

import com.sajo.trading_service.trading.domain.AutoTrading;

import java.time.Instant;
import java.util.UUID;

public record AutoTradingCreateResponse(
        UUID autoTradingId,
        UUID strategyId,
        Boolean enabled,
        Instant createdAt
) {
    public static AutoTradingCreateResponse from(
            AutoTrading autoTrading
    ){
        return new AutoTradingCreateResponse(
                autoTrading.getId(),
                autoTrading.getStrategyId(),
                autoTrading.getEnabled(),
                autoTrading.getCreatedAt()
        );
    }
}
