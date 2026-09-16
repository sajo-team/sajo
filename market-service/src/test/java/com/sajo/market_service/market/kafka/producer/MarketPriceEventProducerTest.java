package com.sajo.market_service.market.kafka.producer;

import com.sajo.market_service.market.domain.PriceSource;
import com.sajo.market_service.market.kafka.dto.MarketPriceUpdatedEvent;
import com.sajo.market_service.market.kafka.dto.MarketPricePayload;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MarketPriceEventProducerTest {

    private static final String TOPIC = "market.price.updated";

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
    private final MarketPriceEventProducer producer = new MarketPriceEventProducer(kafkaTemplate);

    private final MarketPriceUpdatedEvent event = new MarketPriceUpdatedEvent(
            UUID.randomUUID(),
            "MARKET_PRICE_UPDATED",
            1,
            Instant.parse("2026-09-14T15:07:46Z"),
            new MarketPricePayload(
                    "005930", 249250L, -10250L, new BigDecimal("-3.95"),
                    14871538L, Instant.parse("2026-09-14T06:07:46Z"), PriceSource.WEBSOCKET
            )
    );

    @SuppressWarnings("unchecked")
    private CompletableFuture<SendResult<String, Object>> stubFuture() {
        CompletableFuture<SendResult<String, Object>> future = mock(CompletableFuture.class);
        given(kafkaTemplate.send(eq(TOPIC), eq("005930"), eq(event))).willReturn(future);
        return future;
    }

    @Test
    void publishesEventToConfiguredTopicWithStockCodeAsKey() {
        stubFuture();

        producer.publish(event);

        verify(kafkaTemplate).send(TOPIC, "005930", event);
    }

    @Test
    void throwsIllegalStateExceptionWhenKafkaSendFails() throws Exception {
        CompletableFuture<SendResult<String, Object>> future = stubFuture();
        given(future.get(anyLong(), any(TimeUnit.class)))
                .willThrow(new ExecutionException("broker down", new RuntimeException("cause")));

        assertThatThrownBy(() -> producer.publish(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("발행에 실패");
    }

    @Test
    void throwsIllegalStateExceptionWhenPublishTimesOut() throws Exception {
        CompletableFuture<SendResult<String, Object>> future = stubFuture();
        given(future.get(anyLong(), any(TimeUnit.class))).willThrow(new TimeoutException("too slow"));

        assertThatThrownBy(() -> producer.publish(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("시간이 초과");
    }

    @Test
    void restoresInterruptedStatusAndThrowsWhenPublishIsInterrupted() throws Exception {
        CompletableFuture<SendResult<String, Object>> future = stubFuture();
        given(future.get(anyLong(), any(TimeUnit.class))).willThrow(new InterruptedException());

        try {
            assertThatThrownBy(() -> producer.publish(event)).isInstanceOf(IllegalStateException.class);
            assertThat(Thread.interrupted()).isTrue();
        } finally {
            // 다음 테스트에 인터럽트 상태가 새어나가지 않도록 확실히 정리한다.
            Thread.interrupted();
        }
    }
}
