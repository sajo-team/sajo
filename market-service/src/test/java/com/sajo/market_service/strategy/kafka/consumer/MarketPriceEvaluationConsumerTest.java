package com.sajo.market_service.strategy.kafka.consumer;

import com.sajo.market_service.market.domain.PriceSource;
import com.sajo.market_service.market.kafka.dto.MarketPricePayload;
import com.sajo.market_service.market.kafka.dto.MarketPriceUpdatedEvent;
import com.sajo.market_service.strategy.controller.dto.request.StrategyEvaluationRequest;
import com.sajo.market_service.strategy.service.command.StrategyEvaluationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MarketPriceEvaluationConsumerTest {

    @Mock
    private StrategyEvaluationService strategyEvaluationService;

    @Test
    @DisplayName("market.price.updated 이벤트를 StrategyEvaluationRequest로 매핑해 evaluate를 호출한다")
    void mapsEventToEvaluationRequest() {
        MarketPriceEvaluationConsumer consumer = new MarketPriceEvaluationConsumer(strategyEvaluationService);

        UUID eventId = UUID.randomUUID();
        Instant tradedAt = Instant.parse("2026-09-21T00:30:00Z");
        MarketPricePayload payload = new MarketPricePayload(
                "005930", 71_000L, 1_000L, new BigDecimal("1.43"), 123_456L, tradedAt, PriceSource.WEBSOCKET
        );
        MarketPriceUpdatedEvent event =
                new MarketPriceUpdatedEvent(eventId, "MARKET_PRICE_UPDATED", 1, Instant.now(), payload);

        consumer.consume(event);

        ArgumentCaptor<StrategyEvaluationRequest> captor = ArgumentCaptor.forClass(StrategyEvaluationRequest.class);
        verify(strategyEvaluationService).evaluate(captor.capture());

        StrategyEvaluationRequest request = captor.getValue();
        assertThat(request.sourceEventId()).isEqualTo(eventId);
        assertThat(request.stockCode()).isEqualTo("005930");
        assertThat(request.currentPrice()).isEqualTo(71_000L);
        assertThat(request.baseTime()).isEqualTo(tradedAt);
    }
}
