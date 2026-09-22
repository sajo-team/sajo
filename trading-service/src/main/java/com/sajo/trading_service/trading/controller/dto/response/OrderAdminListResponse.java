package com.sajo.trading_service.trading.controller.dto.response;

import com.sajo.trading_service.trading.domain.Order;
import com.sajo.trading_service.trading.domain.enums.OrderStatus;
import com.sajo.trading_service.trading.domain.enums.OrderType;

import java.time.Instant;
import java.util.UUID;

public record OrderAdminListResponse(
        UUID orderId,
        UUID userId,
        UUID autoTradingId,
        UUID strategyId,
        UUID signalId,
        String stockCode,
        OrderType orderType,
        Long signalPrice,
        Long executedOrderPrice,
        Integer orderQuantity,
        Integer filledQuantity,
        Integer remainingQuantity,
        Long estimatedOrderAmount,
        OrderStatus status,
        String brokerOrderNo,
        String failureCode,
        String failureMessage,
        Integer accountRetryCount,
        Integer marketRetryCount,
        Integer reconciliationRetryCount,
        Instant lastExecutionCheckedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static OrderAdminListResponse from(
            Order order
    ) {
        return new OrderAdminListResponse(
                order.getId(),
                order.getUserId(),
                order.getAutoTradingId(),
                order.getStrategyId(),
                order.getSignalId(),
                order.getStockCode(),
                order.getOrderType(),
                order.getSignalPrice(),
                order.getExecutedOrderPrice(),
                order.getOrderQuantity(),
                order.getFilledQuantity(),
                order.getRemainingQuantity(),
                order.getEstimatedOrderAmount(),
                order.getStatus(),
                order.getBrokerOrderNo(),
                order.getFailureCode(),
                order.getFailureMessage(),
                order.getAccountRetryCount(),
                order.getMarketRetryCount(),
                order.getReconciliationRetryCount(),
                order.getLastExecutionCheckedAt(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
