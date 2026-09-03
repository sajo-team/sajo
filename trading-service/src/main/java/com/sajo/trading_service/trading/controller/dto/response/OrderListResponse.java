package com.sajo.trading_service.trading.controller.dto.response;

import com.sajo.trading_service.trading.domain.Order;
import com.sajo.trading_service.trading.domain.enums.OrderStatus;
import com.sajo.trading_service.trading.domain.enums.OrderType;

import java.time.Instant;
import java.util.UUID;

public record OrderListResponse(
        UUID orderId,
        UUID autoTradingId,
        UUID strategyId,
        String stockCode,
        OrderType orderType,
        Long signalPrice,
        Integer orderQuantity,
        Long estimatedOrderAmount,
        OrderStatus status,
        Instant createdAt
) {
    public static OrderListResponse from(Order order){
        return new OrderListResponse(
                order.getId(),
                order.getAutoTradingId(),
                order.getStrategyId(),
                order.getStockCode(),
                order.getOrderType(),
                order.getSignalPrice(),
                order.getOrderQuantity(),
                order.getEstimatedOrderAmount(),
                order.getStatus(),
                order.getCreatedAt()
        );
    }
}
