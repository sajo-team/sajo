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

import static org.assertj.core.api.Assertions.assertThatCode;
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
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
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
    void doesNotBlockCallerWhileKafkaAckIsPending() {
        // send()가 반환하는 future를 절대 완료시키지 않는다 — publish()가 블로킹 get()을 여전히
        // 쓰고 있다면 이 테스트는 타임아웃으로 실패한다(코드 리뷰 반영, #236).
        CompletableFuture<SendResult<String, Object>> future = stubFuture();

        assertThatCode(() -> producer.publish(event)).doesNotThrowAnyException();
        assertThatCode(future::isDone).doesNotThrowAnyException();
    }

    @Test
    void doesNotPropagateWhenKafkaAckCompletesExceptionally() {
        CompletableFuture<SendResult<String, Object>> future = stubFuture();

        assertThatCode(() -> producer.publish(event)).doesNotThrowAnyException();

        // 비동기 ack이 나중에 실패로 도착해도(#236) 이미 리턴한 publish() 호출부에는
        // 아무 영향이 없어야 한다 — 실패 처리는 whenComplete 콜백 내부에서 로깅으로 끝난다.
        assertThatCode(() -> future.completeExceptionally(new RuntimeException("broker down")))
                .doesNotThrowAnyException();
    }

    @Test
    void doesNotPropagateWhenKafkaTemplateSendThrowsSynchronously() {
        given(kafkaTemplate.send(eq(TOPIC), eq("005930"), eq(event)))
                .willThrow(new IllegalStateException("producer is closing"));

        assertThatCode(() -> producer.publish(event)).doesNotThrowAnyException();
    }
}
