package com.sajo.trading_service.ai_risk.client.strategy.dto;

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
        String status
) {
}
