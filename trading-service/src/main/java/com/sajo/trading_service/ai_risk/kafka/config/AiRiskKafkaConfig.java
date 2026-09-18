package com.sajo.trading_service.ai_risk.kafka.config;

import com.sajo.common.kafka.config.KafkaErrorHandlers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
public class AiRiskKafkaConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> aiRiskListenerFactory(
            ConsumerFactory<Object, Object> consumerFactory,
            KafkaTemplate<Object, Object> kafkaTemplate
    ) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(
                KafkaErrorHandlers.withDlt(kafkaTemplate)
        );

        return factory;
    }
}
