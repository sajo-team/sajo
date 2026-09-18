package com.sajo.trading_service.trading.controller.dto.request;

import jakarta.validation.constraints.NotNull;

public record AutoTradingAdminSuspensionRequest(

        @NotNull
        Boolean suspended

) {
}