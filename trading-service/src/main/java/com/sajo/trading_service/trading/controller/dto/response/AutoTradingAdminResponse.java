package com.sajo.trading_service.trading.controller.dto.response;

import com.sajo.trading_service.trading.domain.AutoTrading;
import com.sajo.trading_service.trading.domain.Order;
import com.sajo.trading_service.trading.domain.enums.AutoTradingDirection;
import com.sajo.trading_service.trading.domain.enums.OrderStatus;

import java.time.Instant;
import java.util.UUID;

public record AutoTradingAdminResponse(
        UUID autoTradingId,
        UUID userId,
        UUID strategyId,
        AutoTradingDirection direction,
        Boolean enabled,
        Boolean adminSuspended,

        UUID latestOrderId,
        OrderStatus latestOrderStatus,
        Instant latestOrderCreatedAt,
        String latestFailureCode,
        String latestFailureMessage,

        Instant createdAt,
        Instant updatedAt
) {

    public static AutoTradingAdminResponse from(
            AutoTrading autoTrading,
            Order latestOrder
    ) {
        return new AutoTradingAdminResponse(
                autoTrading.getId(),
                autoTrading.getUserId(),
                autoTrading.getStrategyId(),
                autoTrading.getDirection(),
                autoTrading.getEnabled(),
                autoTrading.getAdminSuspended(),

                latestOrder != null
                        ? latestOrder.getId()
                        : null,

                latestOrder != null
                        ? latestOrder.getStatus()
                        : null,

                latestOrder != null
                        ? latestOrder.getCreatedAt()
                        : null,

                latestOrder != null
                        ? latestOrder.getFailureCode()
                        : null,

                latestOrder != null
                        ? latestOrder.getFailureMessage()
                        : null,

                autoTrading.getCreatedAt(),
                autoTrading.getUpdatedAt()
        );
    }
}