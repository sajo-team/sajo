package com.sajo.trading_service.trading.controller.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AutoTradingCreateRequest(
        @NotNull
        UUID strategyId
) {
}
