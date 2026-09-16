package com.sajo.market_service.market.kafka.dto;

import com.sajo.market_service.market.domain.PriceSource;
import com.sajo.market_service.market.dto.kis.KisRealtimePriceMessage;
import com.sajo.market_service.market.dto.response.QuoteResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MarketPriceUpdatedEventTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-09-14T15:07:46Z");

    private final QuoteResponse quote = new QuoteResponse(
            "005930", 249250L, 249500L, 254500L, 248500L, 259500L, -10250L, new BigDecimal("-3.95"),
            14871538L, 3728926343750L, 4180000L, new BigDecimal("15.20"), new BigDecimal("1.35"),
            new BigDecimal("4605.00"), new BigDecimal("51850.00")
    );

    private KisRealtimePriceMessage messageWithTradeTime(String businessDate, String tradeTime) {
        return new KisRealtimePriceMessage(
                "005930", tradeTime, "249250", "5", "-10250", "-3.95",
                "249500", "254500", "248500", "1", "14871538", "3728926343750", businessDate
        );
    }

    @Test
    void buildsEnvelopeAndPayloadFromQuote() {
        KisRealtimePriceMessage message = messageWithTradeTime("20260914", "150746");

        MarketPriceUpdatedEvent event = MarketPriceUpdatedEvent.from(message, quote, OCCURRED_AT);

        assertThat(event.eventType()).isEqualTo("MARKET_PRICE_UPDATED");
        assertThat(event.eventVersion()).isEqualTo(1);
        assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(event.payload().stockCode()).isEqualTo("005930");
        assertThat(event.payload().currentPrice()).isEqualTo(249250L);
        assertThat(event.payload().changePrice()).isEqualTo(-10250L);
        assertThat(event.payload().changeRate()).isEqualTo(new BigDecimal("-3.95"));
        assertThat(event.payload().volume()).isEqualTo(14871538L);
        assertThat(event.payload().source()).isEqualTo(PriceSource.WEBSOCKET);
    }

    @Test
    void computesTradedAtFromMessageBusinessDateAndTradeTimeInSeoulZone() {
        KisRealtimePriceMessage message = messageWithTradeTime("20260914", "150746");

        MarketPriceUpdatedEvent event = MarketPriceUpdatedEvent.from(message, quote, OCCURRED_AT);

        // 2026-09-14T15:07:46 Asia/Seoul(UTC+9) == 2026-09-14T06:07:46Z
        assertThat(event.payload().tradedAt()).isEqualTo(Instant.parse("2026-09-14T06:07:46Z"));
    }

    @Test
    void fallsBackToOccurredAtWhenTradeTimeIsMalformed() {
        KisRealtimePriceMessage message = messageWithTradeTime("20260914", "invalid");

        MarketPriceUpdatedEvent event = MarketPriceUpdatedEvent.from(message, quote, OCCURRED_AT);

        assertThat(event.payload().tradedAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    void fallsBackToOccurredAtWhenBusinessDateIsMalformed() {
        KisRealtimePriceMessage message = messageWithTradeTime("invalid-date", "150746");

        MarketPriceUpdatedEvent event = MarketPriceUpdatedEvent.from(message, quote, OCCURRED_AT);

        assertThat(event.payload().tradedAt()).isEqualTo(OCCURRED_AT);
    }
}
