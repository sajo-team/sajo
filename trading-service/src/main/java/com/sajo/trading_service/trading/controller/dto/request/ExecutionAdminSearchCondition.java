package com.sajo.trading_service.trading.controller.dto.request;

import com.sajo.trading_service.trading.domain.enums.OrderType;

import java.util.UUID;

public record ExecutionAdminSearchCondition(
        UUID userId,
        UUID orderId,
        UUID autoTradingId,
        UUID strategyId,
        String stockCode,
        OrderType orderType
) {
}
