package com.sajo.market_service.strategy.kafka.config;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class StrategyKafkaConfigTest {

    @Test
    void DLT_적용_컨슈머_팩토리를_생성한다() {
        ConsumerFactory<Object, Object> consumerFactory = mock(ConsumerFactory.class);
        KafkaTemplate<Object, Object> kafkaTemplate = mock(KafkaTemplate.class);

        StrategyKafkaConfig config = new StrategyKafkaConfig();

        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
                config.strategyEvaluationListenerFactory(consumerFactory, kafkaTemplate);

        assertThat(factory).isNotNull();
    }
}
