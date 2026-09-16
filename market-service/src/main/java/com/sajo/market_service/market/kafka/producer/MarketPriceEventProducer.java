package com.sajo.market_service.market.kafka.producer;

import com.sajo.market_service.market.kafka.dto.MarketPriceUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code market.price.updated} 토픽에 {@link MarketPriceUpdatedEvent}를 발행한다(#236).
 * 종목별 이벤트 순서 보장을 위해 stockCode를 Kafka 메시지 키로 사용한다.
 *
 * <p>{@code publish()}는 {@link KafkaTemplate#send}가 반환하는 {@code CompletableFuture}를
 * 블로킹으로 기다리지 않는다(코드 리뷰 반영, #236) — 이 메서드는 KIS WebSocket 메시지 수신
 * 스레드({@code KisWebSocketClient.KisMessageListener.handleTextMessage()})에서 직접, 동기적으로
 * 호출되는데, 실시간 체결가는 {@code strategy.kafka.producer.TradingSignalProducer}가 다루는 트레이딩
 * 시그널보다 훨씬 높은 빈도로 들어오는 hot path라 블로킹 get()으로 ack을 기다리면 Kafka가 잠깐만
 * 느려져도 같은 WebSocket 세션의 이후 프레임(다른 종목 tick 포함)이 전부 지연된다.</p>
 *
 * <p>발행 실패(동기적인 전송 준비 실패든, 비동기 ack 실패든)는 이 클래스가 흡수해 로그만 남기고
 * 호출부({@code MarketRealtimePriceUpdateService})로 전파하지 않는다. WebSocket 메시지 처리
 * 스레드나 이미 끝난 Redis 반영이 Kafka 상태에 영향받아서는 안 되기 때문이다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketPriceEventProducer {

    private static final String TOPIC = "market.price.updated";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(MarketPriceUpdatedEvent event) {
        String stockCode = event.payload().stockCode();
        try {
            kafkaTemplate.send(TOPIC, stockCode, event)
                    .whenComplete((result, throwable) -> {
                        if (throwable != null) {
                            log.warn("MarketPriceUpdatedEvent Kafka 발행에 실패했습니다. stockCode={}",
                                    stockCode, throwable);
                        }
                    });
        } catch (RuntimeException exception) {
            // kafkaTemplate.send() 자체가 동기적으로 실패하는 경우(직렬화 실패, 버퍼 확보 타임아웃 등).
            log.warn("MarketPriceUpdatedEvent Kafka 발행 요청 자체가 실패했습니다. stockCode={}",
                    stockCode, exception);
        }
    }
}
