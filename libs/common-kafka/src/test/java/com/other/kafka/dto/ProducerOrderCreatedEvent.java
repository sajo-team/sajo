package com.other.kafka.dto;

import java.time.Instant;

public record ProducerOrderCreatedEvent(String orderId, Instant occurredAt) {
}
