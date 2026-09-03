package com.sajo.trading_service.trading.controller.dto.response;

import com.sajo.trading_service.trading.domain.AutoTrading;

import java.time.Instant;
import java.util.UUID;

public record AutoTradingUpdateResponse(
        UUID autoTradingId,
        UUID strategyId,
        Boolean enabled,
        Instant updatedAt
) {
    public static AutoTradingUpdateResponse from(AutoTrading autoTrading) {
        return new AutoTradingUpdateResponse(
                autoTrading.getId(),
                autoTrading.getStrategyId(),
                autoTrading.getEnabled(),
                autoTrading.getUpdatedAt()
        );
    }
}