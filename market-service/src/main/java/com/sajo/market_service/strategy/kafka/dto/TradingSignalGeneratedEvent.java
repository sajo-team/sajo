package com.sajo.market_service.strategy.kafka.dto;

import java.time.Instant;
import java.util.UUID;

public record TradingSignalGeneratedEvent(
        UUID eventId,
        String eventType,
        Integer eventVersion,
        Instant occurredAt,
        UUID actorId,
        TradingSignalPayload payload
) {
}
