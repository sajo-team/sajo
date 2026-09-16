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
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class MarketPriceEventProducerTest {

    private static final String TOPIC = "market.price.updated";

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
    private final Executor publishExecutor = mock(Executor.class);
    private final MarketPriceEventProducer producer = new MarketPriceEventProducer(kafkaTemplate, publishExecutor);

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

    /**
     * publish()를 호출한 스레드(테스트에서는 이 메서드 자체)가 kafkaTemplate.send()를 직접 건드리지
     * 않는지 검증한다 — send() 자체가 max.block.ms까지 블로킹될 수 있으므로(코드 리뷰 반영, #239),
     * 그 호출은 반드시 전용 publishExecutor로 위임되어야 한다.
     */
    @Test
    void delegatesSendToPublishExecutorInsteadOfCallingThread() {
        producer.publish(event);

        verify(publishExecutor).execute(any(Runnable.class));
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void sendsEventToConfiguredTopicWithStockCodeAsKeyWhenExecutorRunsTheTask() {
        stubFuture();
        Runnable[] captured = new Runnable[1];
        org.mockito.Mockito.doAnswer(invocation -> {
            captured[0] = invocation.getArgument(0);
            return null;
        }).when(publishExecutor).execute(any(Runnable.class));

        producer.publish(event);
        captured[0].run();

        verify(kafkaTemplate).send(TOPIC, "005930", event);
    }

    @Test
    void doesNotPropagateWhenKafkaAckCompletesExceptionally() {
        CompletableFuture<SendResult<String, Object>> future = stubFuture();
        Runnable[] captured = new Runnable[1];
        org.mockito.Mockito.doAnswer(invocation -> {
            captured[0] = invocation.getArgument(0);
            return null;
        }).when(publishExecutor).execute(any(Runnable.class));

        producer.publish(event);
        captured[0].run();

        // 비동기 ack이 나중에 실패로 도착해도(#236) publishExecutor 스레드에는 아무 영향이 없어야
        // 한다 — 실패 처리는 whenComplete 콜백 내부에서 로깅으로 끝난다.
        assertThatCode(() -> future.completeExceptionally(new RuntimeException("broker down")))
                .doesNotThrowAnyException();
    }

    @Test
    void doesNotPropagateWhenKafkaTemplateSendBlocksOnMetadataAndThenThrows() {
        // send() 자체가 토픽 메타데이터 미보유로 max.block.ms까지 블로킹되다 실패하는 상황을
        // 흉내낸다(코드 리뷰 반영, #239). 이 예외가 publishExecutor 스레드 밖으로 새어나가지
        // 않아야 한다.
        given(kafkaTemplate.send(eq(TOPIC), eq("005930"), eq(event)))
                .willThrow(new org.apache.kafka.common.errors.TimeoutException("Topic metadata not present"));
        Runnable[] captured = new Runnable[1];
        org.mockito.Mockito.doAnswer(invocation -> {
            captured[0] = invocation.getArgument(0);
            return null;
        }).when(publishExecutor).execute(any(Runnable.class));

        producer.publish(event);

        assertThatCode(() -> captured[0].run()).doesNotThrowAnyException();
    }
}
