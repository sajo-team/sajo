package com.other.kafka.dto;

import java.time.Instant;

public record ConsumerOrderCreatedEvent(String orderId, Instant occurredAt) {
}
