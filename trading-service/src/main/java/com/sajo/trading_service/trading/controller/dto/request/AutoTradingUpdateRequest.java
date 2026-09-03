package com.sajo.trading_service.trading.controller.dto.request;

import jakarta.validation.constraints.NotNull;

public record AutoTradingUpdateRequest(
        @NotNull
        Boolean enabled
) {
}
