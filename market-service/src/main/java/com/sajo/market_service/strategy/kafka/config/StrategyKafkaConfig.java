package com.sajo.market_service.strategy.kafka.config;

import com.sajo.common.kafka.config.KafkaErrorHandlers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * {@link com.sajo.market_service.strategy.kafka.consumer.MarketPriceEvaluationConsumer}
 * (strategy 소유)에만 재시도 후 DLT(Dead Letter Topic)를 적용하기 위한 전용
 * {@code containerFactory}. market 패키지의 다른 컨슈머(예: MarketQuoteRequestConsumer)는
 * market 담당 영역이라 이 서비스 전체의 기본 에러 핸들러(재시도 후 스킵)를 그대로 둔다
 * — trading-service의 {@code AiRiskKafkaConfig}와 동일한, 이름 있는 factory로
 * 적용 범위를 좁히는 패턴.
 */
@Configuration
public class StrategyKafkaConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> strategyEvaluationListenerFactory(
            ConsumerFactory<Object, Object> consumerFactory,
            KafkaTemplate<Object, Object> kafkaTemplate
    ) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(KafkaErrorHandlers.withDlt(kafkaTemplate));

        return factory;
    }
}
