package com.sajo.trading_service.trading.controller.dto.response;

import com.sajo.trading_service.trading.domain.enums.OrderType;
import com.sajo.trading_service.trading.repository.query.projection.ExecutionAdminQueryProjection;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ExecutionAdminResponse(
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

    public static ExecutionAdminResponse from(
            ExecutionAdminQueryProjection projection
    ) {
        return new ExecutionAdminResponse(
                projection.executionId(),
                projection.orderId(),
                projection.userId(),
                projection.autoTradingId(),
                projection.strategyId(),
                projection.stockCode(),
                projection.orderType(),
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
