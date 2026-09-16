package com.sajo.market_service.market.kafka.producer;

import com.sajo.market_service.market.kafka.dto.MarketPriceUpdatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * {@code market.price.updated} 토픽에 {@link MarketPriceUpdatedEvent}를 발행한다(#236).
 * 종목별 이벤트 순서 보장을 위해 stockCode를 Kafka 메시지 키로 사용한다.
 *
 * <p>{@code strategy.kafka.producer.TradingSignalProducer}와 동일한 패턴(블로킹 get + 타임아웃)을
 * 따른다. 호출부({@code MarketRealtimePriceUpdateService})가 발행 실패를 흡수해 WebSocket 메시지
 * 처리 스레드를 죽이지 않도록, 이 클래스는 발행 실패를 예외로 던지기만 하고 흡수하지 않는다.</p>
 */
@Component
@RequiredArgsConstructor
public class MarketPriceEventProducer {

    private static final String TOPIC = "market.price.updated";
    private static final long PUBLISH_TIMEOUT_SECONDS = 10L;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(MarketPriceUpdatedEvent event) {
        try {
            kafkaTemplate.send(
                    TOPIC,
                    event.payload().stockCode(),
                    event
            ).get(PUBLISH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("MarketPriceUpdatedEvent Kafka 발행이 중단되었습니다.", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("MarketPriceUpdatedEvent Kafka 발행에 실패했습니다.", exception.getCause());
        } catch (TimeoutException exception) {
            throw new IllegalStateException("MarketPriceUpdatedEvent Kafka 발행 시간이 초과되었습니다.", exception);
        }
    }
}
