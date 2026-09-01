package com.sajo.market_service.strategy.controller.dto.response;

import com.sajo.market_service.strategy.domain.Strategy;
import com.sajo.market_service.strategy.domain.StrategyStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StrategyCreateResponse(
        UUID strategyId,
        UUID stockId,
        String stockCode,
        String strategyName,
        Long buyConditionPrice,
        Long sellConditionPrice,
        BigDecimal stopLossRate,
        BigDecimal targetReturnRate,
        Long allocatedAmount,
        BigDecimal perCondition,
        BigDecimal pbrCondition,
        BigDecimal roeCondition,
        StrategyStatus status,
        Instant createdAt
) {
    public static StrategyCreateResponse from(Strategy strategy) {
        return new StrategyCreateResponse(
                strategy.getId(),
                strategy.getStockId(),
                strategy.getStockCode(),
                strategy.getStrategyName(),
                strategy.getBuyConditionPrice(),
                strategy.getSellConditionPrice(),
                strategy.getStopLossRate(),
                strategy.getTargetReturnRate(),
                strategy.getAllocatedAmount(),
                strategy.getPerCondition(),
                strategy.getPbrCondition(),
                strategy.getRoeCondition(),
                strategy.getStatus(),
                strategy.getCreatedAt()
        );
    }
}
