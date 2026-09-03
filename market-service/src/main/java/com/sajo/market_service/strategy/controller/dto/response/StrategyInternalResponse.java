package com.sajo.market_service.strategy.controller.dto.response;

import com.sajo.market_service.strategy.domain.Strategy;
import com.sajo.market_service.strategy.domain.StrategyStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record StrategyInternalResponse(
        UUID strategyId,
        UUID userId,
        String stockCode,
        String strategyName,
        Long buyConditionPrice,
        Long sellConditionPrice,
        BigDecimal stopLossRate,
        BigDecimal targetReturnRate,
        Long allocatedAmount,
        Long orderAmount,
        BigDecimal perCondition,
        BigDecimal pbrCondition,
        BigDecimal roeCondition,
        StrategyStatus status
) {
    public static StrategyInternalResponse from(Strategy strategy) {
        return new StrategyInternalResponse(
                strategy.getId(),
                strategy.getUserId(),
                strategy.getStockCode(),
                strategy.getStrategyName(),
                strategy.getBuyConditionPrice(),
                strategy.getSellConditionPrice(),
                strategy.getStopLossRate(),
                strategy.getTargetReturnRate(),
                strategy.getAllocatedAmount(),
                strategy.getOrderAmount(),
                strategy.getPerCondition(),
                strategy.getPbrCondition(),
                strategy.getRoeCondition(),
                strategy.getStatus()
        );
    }
}
