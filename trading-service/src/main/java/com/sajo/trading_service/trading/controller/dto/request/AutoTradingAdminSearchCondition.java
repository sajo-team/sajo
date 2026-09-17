package com.sajo.trading_service.trading.controller.dto.request;

import com.sajo.trading_service.trading.domain.enums.AutoTradingDirection;

import java.util.UUID;

public record AutoTradingAdminSearchCondition(
        UUID userId,
        UUID strategyId,
        AutoTradingDirection direction,
        Boolean enabled
) {
}
