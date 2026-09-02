package com.sajo.market_service.strategy.controller.dto.response;

import com.sajo.market_service.strategy.domain.Strategy;
import com.sajo.market_service.strategy.domain.StrategyStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record StrategyDetailResponse(
        UUID strategyId,
        String stockCode,
        String strategyName,
        Long buyConditionPrice,
        Long sellConditionPrice,
        BigDecimal stopLossRate,
        BigDecimal targetReturnRate,
        Long allocatedAmount,
        StrategyStatus status
) {
    public static StrategyDetailResponse from(Strategy strategy) {
        return new StrategyDetailResponse(
                strategy.getId(),
                strategy.getStockCode(),
                strategy.getStrategyName(),
                strategy.getBuyConditionPrice(),
                strategy.getSellConditionPrice(),
                strategy.getStopLossRate(),
                strategy.getTargetReturnRate(),
                strategy.getAllocatedAmount(),
                strategy.getStatus()
        );
    }
}
