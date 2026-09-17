package com.sajo.trading_service.outbox.domain;

import java.util.UUID;

public record OutboxMessage(
        UUID eventId,
        String eventType,
        int eventVersion,
        Object eventBody // envelope 객체
) {
}
