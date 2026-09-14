package com.sajo.trading_service.trading.repository.query.projection;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ExecutionQueryProjection(
        UUID executionId,
        UUID orderId,
        UUID autoTradingId,
        UUID strategyId,
        String brokerOrderNo,
        Integer executedQuantity,
        BigDecimal averageExecutionPrice,
        Long totalExecutionAmount,
        Integer remainingQuantity,
        Instant createdAt,
        Instant updatedAt
) {
}