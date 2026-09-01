package com.sajo.other.redis;

import java.time.Instant;

public record TestValue(String name, int number, Instant createdAt) {
}
