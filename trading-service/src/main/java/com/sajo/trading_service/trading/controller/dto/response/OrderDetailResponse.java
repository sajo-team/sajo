package com.sajo.trading_service.trading.controller.dto.response;

import com.sajo.trading_service.trading.domain.Order;
import com.sajo.trading_service.trading.domain.enums.OrderStatus;
import com.sajo.trading_service.trading.domain.enums.OrderType;

import java.time.Instant;
import java.util.UUID;

public record OrderDetailResponse(
        UUID orderId,
        UUID autoTradingId,
        UUID strategyId,
        UUID signalId,
        String stockCode,
        OrderType orderType,
        Long signalPrice,
        Integer orderQuantity,
        Long estimatedOrderAmount,
        OrderStatus status,
        String brokerOrderNo,
        String failureCode,
        String failureMessage,
        Instant createdAt,
        Instant updatedAt
) {
    public static OrderDetailResponse from(Order order) {
        return new OrderDetailResponse(
                order.getId(),
                order.getAutoTradingId(),
                order.getStrategyId(),
                order.getSignalId(),
                order.getStockCode(),
                order.getOrderType(),
                order.getSignalPrice(),
                order.getOrderQuantity(),
                order.getEstimatedOrderAmount(),
                order.getStatus(),
                order.getBrokerOrderNo(),
                order.getFailureCode(),
                order.getFailureMessage(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
