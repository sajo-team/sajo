package com.sajo.other.kafka;

import java.time.Instant;

public record KafkaTestMessage(String content, Instant createdAt) {
}
