package com.sajo.trading_service.trading.kafka.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.UUID;

public record TradingSignalGeneratedEvent(
        @NotNull UUID eventId,
        @NotBlank String eventType,
        @NotNull @Positive Integer eventVersion,
        @NotNull Instant occurredAt,
        @NotNull UUID actorId,
        @NotNull @Valid TradingSignalPayload payload
) {
}
