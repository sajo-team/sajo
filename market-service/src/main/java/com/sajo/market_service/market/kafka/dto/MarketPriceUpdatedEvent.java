package com.sajo.market_service.market.kafka.dto;

import com.sajo.market_service.market.domain.PriceSource;
import com.sajo.market_service.market.dto.kis.KisRealtimePriceMessage;
import com.sajo.market_service.market.dto.response.QuoteResponse;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Trading에 실시간 체결가를 전달하는 Kafka 이벤트(#236, Kafka Event Naming Convention 확정 스펙).
 * Topic: {@code market.price.updated}, Producer: Market, Consumer: Trading({@code trading-consumer-group}).
 */
@Slf4j
public record MarketPriceUpdatedEvent(
        UUID eventId,
        String eventType,
        Integer eventVersion,
        Instant occurredAt,
        MarketPricePayload payload
) {

    private static final String EVENT_TYPE = "MARKET_PRICE_UPDATED";
    private static final int EVENT_VERSION = 1;
    private static final ZoneId KRX_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TRADE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HHmmss");

    /**
     * {@code message}(KIS WebSocket 원문)에서 실제 체결 시각(businessDate+tradeTime)을 우선 사용하고,
     * 파싱에 실패하면 {@code occurredAt}(이 이벤트를 발행하는 시각)으로 대체한다. REST 경로의
     * baseTime과 달리 WebSocket 원문에는 실제 체결 시각 필드가 있으므로 이를 그대로 활용한다.
     */
    public static MarketPriceUpdatedEvent from(KisRealtimePriceMessage message, QuoteResponse quote, Instant occurredAt) {
        MarketPricePayload payload = new MarketPricePayload(
                quote.stockCode(),
                quote.currentPrice(),
                quote.changePrice(),
                quote.changeRate(),
                quote.accumulatedVolume(),
                toTradedAt(message, occurredAt),
                PriceSource.WEBSOCKET
        );
        return new MarketPriceUpdatedEvent(UUID.randomUUID(), EVENT_TYPE, EVENT_VERSION, occurredAt, payload);
    }

    private static Instant toTradedAt(KisRealtimePriceMessage message, Instant fallback) {
        try {
            LocalDate businessDate = LocalDate.parse(message.businessDate(), DateTimeFormatter.BASIC_ISO_DATE);
            LocalTime tradeTime = LocalTime.parse(message.tradeTime(), TRADE_TIME_FORMATTER);
            return businessDate.atTime(tradeTime).atZone(KRX_ZONE).toInstant();
        } catch (RuntimeException exception) {
            log.warn("체결시각 파싱 실패, occurredAt으로 대체합니다. stockCode={}, businessDate={}, tradeTime={}",
                    message.stockCode(), message.businessDate(), message.tradeTime(), exception);
            return fallback;
        }
    }
}
