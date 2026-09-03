package com.sajo.trading_service.trading.kafka.config;

import com.sajo.common.kafka.config.KafkaErrorHandlers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;

@Configuration
public class TradingKafkaConfig {

    @Bean
    public CommonErrorHandler tradingKafkaErrorHandler(
            KafkaTemplate<Object, Object> kafkaTemplate
    ){
        return KafkaErrorHandlers.withDlt(kafkaTemplate);
    }
}
