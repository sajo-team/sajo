package com.sajo.trading_service.trading.controller.dto.request;

import com.sajo.trading_service.trading.domain.enums.AutoTradingDirection;

public record AutoTradingUpdateRequest(
        Boolean enabled,
        AutoTradingDirection direction
) {
}