package com.sajo.market_service.strategy.kafka.producer;

import com.sajo.market_service.strategy.kafka.dto.TradingSignalGeneratedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.kafka.core.KafkaTemplate;


@Component
@RequiredArgsConstructor
public class TradingSignalProducer {

    private static final String TOPIC = "trading.signal.generated";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(TradingSignalGeneratedEvent event) {
        kafkaTemplate.send(
                TOPIC,
                event.payload().strategyId().toString(),
                event
        );
    }
}
