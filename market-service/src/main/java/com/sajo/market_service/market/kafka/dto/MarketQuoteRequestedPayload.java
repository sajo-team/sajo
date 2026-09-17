package com.sajo.market_service.market.kafka.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * {@link MarketQuoteRequestedEvent}의 payload(#248).
 */
public record MarketQuoteRequestedPayload(
        UUID userId,
        String stockCode,
        Instant requestedAt
) {
}
