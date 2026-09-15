package com.sajo.trading_service.trading.controller.dto.response;

import com.sajo.trading_service.trading.domain.AutoTrading;
import com.sajo.trading_service.trading.domain.enums.AutoTradingDirection;

import java.time.Instant;
import java.util.UUID;

public record AutoTradingUpdateResponse(
        UUID autoTradingId,
        UUID strategyId,
        AutoTradingDirection direction,
        Boolean enabled,
        Instant updatedAt
) {
    public static AutoTradingUpdateResponse from(AutoTrading autoTrading) {
        return new AutoTradingUpdateResponse(
                autoTrading.getId(),
                autoTrading.getStrategyId(),
                autoTrading.getDirection(),
                autoTrading.getEnabled(),
                autoTrading.getUpdatedAt()
        );
    }
}