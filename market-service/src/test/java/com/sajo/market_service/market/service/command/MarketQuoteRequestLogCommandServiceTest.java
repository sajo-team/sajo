package com.sajo.market_service.market.service.command;

import com.sajo.market_service.market.kafka.dto.MarketQuoteRequestedEvent;
import com.sajo.market_service.market.kafka.producer.MarketQuoteRequestEventProducer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MarketQuoteRequestLogCommandServiceTest {

    private final MarketQuoteRequestEventProducer producer = mock(MarketQuoteRequestEventProducer.class);
    private final MarketQuoteRequestLogCommandService service =
            new MarketQuoteRequestLogCommandService(producer);

    @Test
    void publishesEventWithGivenUserIdAndStockCode() {
        UUID userId = UUID.randomUUID();

        service.recordQuoteRequest(userId, "005930");

        ArgumentCaptor<MarketQuoteRequestedEvent> captor = ArgumentCaptor.forClass(MarketQuoteRequestedEvent.class);
        verify(producer).publish(captor.capture());

        MarketQuoteRequestedEvent event = captor.getValue();
        assertThat(event.eventType()).isEqualTo("MARKET_QUOTE_REQUESTED");
        assertThat(event.payload().userId()).isEqualTo(userId);
        assertThat(event.payload().stockCode()).isEqualTo("005930");
        assertThat(event.payload().requestedAt()).isNotNull();
    }
}
