package com.sajo.market_service.market.kafka.producer;

import com.sajo.market_service.market.kafka.dto.MarketQuoteRequestedEvent;
import com.sajo.market_service.market.kafka.dto.MarketQuoteRequestedPayload;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * {@link MarketPriceEventProducer}에 대한 기존 테스트(#239)와 동일한 시나리오를
 * {@link MarketQuoteRequestEventProducer}에도 적용한다(코드 리뷰 반영, #248) — 이 Producer는
 * JMeter로 고의로 고부하를 거는 현재가 조회 API에 직결되어 있으므로, 발행이 응답 스레드를
 * 블로킹하거나 예외를 전파하지 않는다는 보장이 특히 중요하다.
 */
class MarketQuoteRequestEventProducerTest {

    private static final String TOPIC = "market.quote.requested";

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
    private final Executor publishExecutor = mock(Executor.class);
    private final MarketQuoteRequestEventProducer producer =
            new MarketQuoteRequestEventProducer(kafkaTemplate, publishExecutor);

    private final MarketQuoteRequestedEvent event = new MarketQuoteRequestedEvent(
            UUID.randomUUID(),
            "MARKET_QUOTE_REQUESTED",
            1,
            Instant.parse("2026-09-17T01:00:00Z"),
            new MarketQuoteRequestedPayload(
                    UUID.randomUUID(), "005930", Instant.parse("2026-09-17T01:00:00Z")
            )
    );

    @SuppressWarnings("unchecked")
    private CompletableFuture<SendResult<String, Object>> stubFuture() {
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        given(kafkaTemplate.send(eq(TOPIC), eq("005930"), eq(event))).willReturn(future);
        return future;
    }

    /**
     * publish()를 호출한 스레드(현재가 조회 응답 경로)가 kafkaTemplate.send()를 직접 건드리지
     * 않는지 검증한다 — send() 자체가 max.block.ms까지 블로킹될 수 있으므로, 그 호출은 반드시
     * 전용 publishExecutor로 위임되어야 한다.
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

        assertThatCode(() -> future.completeExceptionally(new RuntimeException("broker down")))
                .doesNotThrowAnyException();
    }

    @Test
    void doesNotPropagateWhenKafkaTemplateSendBlocksOnMetadataAndThenThrows() {
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

    /**
     * 발행 큐(marketQuoteRequestEventPublishExecutor)가 가득 차 executor.execute() 자체가
     * RejectedExecutionException을 던지는 경우에도, 그 예외가 호출 스레드(현재가 조회 응답 경로)로
     * 전파되지 않아야 한다(코드 리뷰 반영, #248).
     */
    @Test
    void doesNotPropagateWhenPublishQueueRejectsTheTask() {
        willThrow(new RejectedExecutionException("queue is full"))
                .given(publishExecutor).execute(any(Runnable.class));

        assertThatCode(() -> producer.publish(event)).doesNotThrowAnyException();
        verifyNoInteractions(kafkaTemplate);
    }
}
