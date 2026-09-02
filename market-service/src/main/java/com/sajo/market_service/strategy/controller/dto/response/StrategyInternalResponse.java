package com.sajo.market_service.strategy.controller.dto.response;

import com.sajo.market_service.strategy.domain.Strategy;
import com.sajo.market_service.strategy.domain.StrategyStatus;

import java.util.UUID;

public record StrategyInternalResponse(
        UUID strategyId,
        UUID userId,
        String stockCode,
        StrategyStatus status,
        Long allocatedAmount,
        Long orderAmount
) {
    public static StrategyInternalResponse from(Strategy strategy) {
        return new StrategyInternalResponse(
                strategy.getId(),
                strategy.getUserId(),
                strategy.getStockCode(),
                strategy.getStatus(),
                strategy.getAllocatedAmount(),
                strategy.getOrderAmount()
        );
    }
}
