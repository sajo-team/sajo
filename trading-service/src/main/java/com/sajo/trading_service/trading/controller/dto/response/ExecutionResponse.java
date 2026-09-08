package com.sajo.trading_service.trading.controller.dto.response;

import com.sajo.trading_service.trading.domain.Execution;

import java.time.Instant;
import java.util.UUID;

public record ExecutionResponse(
        UUID executionId,
        UUID orderId,
        Integer executedQuantity,
        Long averageExecutionPrice,
        Long totalExecutionAmount,
        Integer remainingQuantity,
        Instant createdAt,
        Instant updatedAt
) {
    public static ExecutionResponse from(Execution execution) {
        return new ExecutionResponse(
                execution.getId(),
                execution.getOrderId(),
                execution.getExecutedQuantity(),
                execution.getAverageExecutionPrice(),
                execution.getTotalExecutionAmount(),
                execution.getRemainingQuantity(),
                execution.getCreatedAt(),
                execution.getUpdatedAt()
        );
    }
}