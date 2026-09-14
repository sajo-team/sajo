package com.sajo.trading_service.trading.controller.dto.response;

import com.sajo.trading_service.trading.repository.query.projection.ExecutionQueryProjection;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ExecutionResponse(
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
    public static ExecutionResponse from(ExecutionQueryProjection projection) {
        return new ExecutionResponse(
                projection.executionId(),
                projection.orderId(),
                projection.autoTradingId(),
                projection.strategyId(),
                projection.brokerOrderNo(),
                projection.executedQuantity(),
                projection.averageExecutionPrice(),
                projection.totalExecutionAmount(),
                projection.remainingQuantity(),
                projection.createdAt(),
                projection.updatedAt()
        );
    }
}