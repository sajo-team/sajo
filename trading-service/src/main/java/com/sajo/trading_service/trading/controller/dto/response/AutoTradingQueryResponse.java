package com.sajo.trading_service.trading.controller.dto.response;

import com.sajo.trading_service.trading.domain.AutoTrading;
import com.sajo.trading_service.trading.domain.Order;
import com.sajo.trading_service.trading.domain.enums.AutoTradingDirection;
import com.sajo.trading_service.trading.domain.enums.OrderStatus;

import java.time.Instant;
import java.util.UUID;

public record AutoTradingQueryResponse(
        UUID autoTradingId,
        UUID strategyId,
        AutoTradingDirection direction,
        Boolean enabled,

        UUID latestOrderId,
        OrderStatus latestOrderStatus,
        Instant latestOrderCreatedAt,
        String latestFailureCode,
        String latestFailureMessage,

        Instant createdAt,
        Instant updatedAt
) {

    public static AutoTradingQueryResponse from(
            AutoTrading autoTrading
    ) {
        return from(autoTrading, null);
    }

    public static AutoTradingQueryResponse from(
            AutoTrading autoTrading,
            Order latestOrder
    ) {
        return new AutoTradingQueryResponse(
                autoTrading.getId(),
                autoTrading.getStrategyId(),
                autoTrading.getDirection(),
                autoTrading.getEnabled(),

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