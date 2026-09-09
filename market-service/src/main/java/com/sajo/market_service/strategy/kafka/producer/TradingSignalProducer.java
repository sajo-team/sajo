package com.sajo.market_service.strategy.kafka.producer;

import com.sajo.market_service.strategy.kafka.dto.TradingSignalGeneratedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.ExecutionException;

@Component
@RequiredArgsConstructor
public class TradingSignalProducer {

    private static final String TOPIC = "trading.signal.generated";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(TradingSignalGeneratedEvent event) {
        try {
            kafkaTemplate.send(
                    TOPIC,
                    event.payload().strategyId().toString(),
                    event
            ).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Trading Signal Kafka 발행이 중단되었습니다.", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Trading Signal Kafka 발행에 실패했습니다.", exception.getCause());
        }
    }
}
