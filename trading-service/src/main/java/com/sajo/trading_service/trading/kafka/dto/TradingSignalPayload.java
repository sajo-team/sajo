package com.sajo.trading_service.trading.kafka.dto;

import com.sajo.trading_service.trading.domain.enums.OrderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record TradingSignalPayload(
        @NotNull UUID signalId,
        @NotNull UUID strategyId,
        @NotNull UUID userId,
        @NotBlank String stockCode,
        @NotNull OrderType signalType,
        @NotNull @Positive Long triggerPrice,
        @NotNull @Positive Long orderAmount,
        String signalReason
){
}
