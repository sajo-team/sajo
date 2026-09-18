package com.sajo.trading_service.trading.repository.query.projection;

import com.sajo.trading_service.trading.domain.enums.OrderType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ExecutionAdminQueryProjection(
        UUID executionId,
        UUID orderId,
        UUID userId,
        UUID autoTradingId,
        UUID strategyId,
        String stockCode,
        OrderType orderType,
        String brokerOrderNo,
        Integer executedQuantity,
        BigDecimal averageExecutionPrice,
        Long totalExecutionAmount,
        Integer remainingQuantity,
        Instant createdAt,
        Instant updatedAt
) {
}