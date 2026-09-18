package com.sajo.trading_service.ai_risk.kafka.config;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@Tag("ai-risk")
@Tag("unit")
class AiRiskKafkaConfigTest {

    @Test
    void AI_Risk_Listener_Factory를_생성한다() {
        ConsumerFactory<Object, Object> consumerFactory =
                mock(ConsumerFactory.class);

        KafkaTemplate<Object, Object> kafkaTemplate =
                mock(KafkaTemplate.class);

        AiRiskKafkaConfig config = new AiRiskKafkaConfig();

        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
                config.aiRiskListenerFactory(
                        consumerFactory,
                        kafkaTemplate
                );

        assertThat(factory).isNotNull();
    }
}