package com.sajo.market_service.market.kafka.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * 현재가 조회(GET /api/v1/market/quote) 요청 이력을 비동기로 기록하기 위한 Kafka 이벤트(#248).
 * Topic: {@code market.quote.requested}, Producer/Consumer 모두 Market 서비스 내부에서 처리한다.
 *
 * <p>이 이벤트는 응답 경로를 막지 않기 위해 발행되는 감사/통계용 이벤트로, 조회 결과 자체(가격 등)는
 * 담지 않는다 — 누가, 어떤 종목을, 언제 조회했는지만 기록한다.</p>
 */
public record MarketQuoteRequestedEvent(
        UUID eventId,
        String eventType,
        Integer eventVersion,
        Instant occurredAt,
        MarketQuoteRequestedPayload payload
) {

    private static final String EVENT_TYPE = "MARKET_QUOTE_REQUESTED";
    private static final int EVENT_VERSION = 1;

    public static MarketQuoteRequestedEvent of(UUID userId, String stockCode, Instant occurredAt) {
        return new MarketQuoteRequestedEvent(
                UUID.randomUUID(),
                EVENT_TYPE,
                EVENT_VERSION,
                occurredAt,
                new MarketQuoteRequestedPayload(userId, stockCode, occurredAt)
        );
    }
}
