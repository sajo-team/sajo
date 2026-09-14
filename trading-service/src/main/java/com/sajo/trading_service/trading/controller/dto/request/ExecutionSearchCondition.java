package com.sajo.trading_service.trading.controller.dto.request;

import java.util.UUID;

public record ExecutionSearchCondition(
        UUID orderId,
        UUID autoTradingId,
        UUID strategyId
) {
}