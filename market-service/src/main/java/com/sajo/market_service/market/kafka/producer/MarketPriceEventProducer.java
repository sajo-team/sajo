package com.sajo.market_service.market.kafka.producer;

import com.sajo.market_service.market.kafka.dto.MarketPriceUpdatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

/**
 * {@code market.price.updated} 토픽에 {@link MarketPriceUpdatedEvent}를 발행한다(#236).
 * 종목별 이벤트 순서 보장을 위해 stockCode를 Kafka 메시지 키로 사용한다.
 *
 * <p>이 메서드는 KIS WebSocket 메시지 수신 스레드({@code KisWebSocketClient.KisMessageListener.handleTextMessage()})에서 직접, 동기적으로 호출된다.
 * 실시간 체결가는 {@code strategy.kafka.producer.TradingSignalProducer}가 다루는 트레이딩 시그널보다 훨씬 높은 빈도로 들어오는 hot path라,
 * 이 호출이 그 스레드를 조금이라도 블로킹하면 Kafka가 잠깐만 느려져도 같은 WebSocket 세션의 이후 프레임(다른 종목 tick 포함)이 전부 지연된다.</p>
 *
 * <p>단순히 {@code KafkaTemplate#send()}가 반환하는 {@code CompletableFuture}를 논블로킹으로 처리하는 것만으로는 부족하다
 * {@code send()} 호출 자체가 토픽 메타데이터 미보유/만료 시 {@code max.block.ms}(기본 60s)까지 호출 스레드에서 동기적으로 블로킹될 수 있기 때문이다
 * 그래서 {@code send()} 호출 자체를 {@link #publishExecutor} 전용 스레드로 위임해, 그 블로킹이 어떤 경우에도 WebSocket 수신 스레드로 전파되지 않도록 격리한다.</p>
 *
 * <p>발행 실패(동기적인 전송 준비 실패든, 비동기 ack 실패든)는 이 클래스가 흡수해 로그만 남기고
 * 호출부({@code MarketRealtimePriceUpdateService})로 전파하지 않는다.</p>
 */
@Slf4j
@Component
public class MarketPriceEventProducer {

    private static final String TOPIC = "market.price.updated";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final Executor publishExecutor;

    public MarketPriceEventProducer(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Qualifier("marketPriceEventPublishExecutor") Executor publishExecutor
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.publishExecutor = publishExecutor;
    }

    public void publish(MarketPriceUpdatedEvent event) {
        String stockCode = event.payload().stockCode();
        publishExecutor.execute(() -> send(stockCode, event));
    }

    private void send(String stockCode, MarketPriceUpdatedEvent event) {
        try {
            kafkaTemplate.send(TOPIC, stockCode, event)
                    .whenComplete((result, throwable) -> {
                        if (throwable != null) {
                            log.warn("MarketPriceUpdatedEvent Kafka 발행에 실패했습니다. stockCode={}",
                                    stockCode, throwable);
                        }
                    });
        } catch (RuntimeException exception) {
            // kafkaTemplate.send() 자체가 실패하는 경우(메타데이터 조회 타임아웃, 직렬화 실패 등).
            // 이 스레드는 전용 publishExecutor이므로, 여기서 예외가 나도 WebSocket 수신 스레드에는 영향이 없다.
            log.warn("MarketPriceUpdatedEvent Kafka 발행 요청 자체가 실패했습니다. stockCode={}",
                    stockCode, exception);
        }
    }
}
