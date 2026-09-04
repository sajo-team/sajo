package com.sajo.market_service.strategy.controller.dto.response;

import com.sajo.market_service.strategy.domain.Strategy;
import com.sajo.market_service.strategy.domain.StrategyStatus;

import java.time.Instant;
import java.util.UUID;

public record StrategyActivationResponse(
        UUID strategyId,
        StrategyStatus status,
        Instant activatedAt
) {
    public static StrategyActivationResponse from(Strategy strategy) {
        return new StrategyActivationResponse(
                strategy.getId(),
                strategy.getStatus(),
                strategy.getActivatedAt()
        );
    }
}
