package com.sajo.market_service.strategy.controller.dto.response;

import com.sajo.market_service.strategy.domain.Strategy;
import com.sajo.market_service.strategy.domain.StrategyStatus;

import java.util.UUID;

public record StrategySummaryResponse(
        UUID strategyId,
        String strategyName,
        String stockCode,
        StrategyStatus status,
        Long allocatedAmount
) {
    public static StrategySummaryResponse from(Strategy strategy) {
        return new StrategySummaryResponse(
                strategy.getId(),
                strategy.getStrategyName(),
                strategy.getStockCode(),
                strategy.getStatus(),
                strategy.getAllocatedAmount()
        );
    }
}
