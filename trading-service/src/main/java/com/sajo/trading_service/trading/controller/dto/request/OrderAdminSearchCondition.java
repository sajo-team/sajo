package com.sajo.trading_service.trading.controller.dto.request;

import com.sajo.trading_service.trading.domain.enums.OrderStatus;
import com.sajo.trading_service.trading.domain.enums.OrderType;

import java.util.UUID;

public record OrderAdminSearchCondition(
        UUID userId,
        UUID autoTradingId,
        UUID strategyId,
        OrderStatus status,
        String stockCode,
        OrderType orderType,
        String brokerOrderNo,
        String failureCode
) {
}
