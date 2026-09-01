package com.sajo.common.kafka.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.kafka.autoconfigure.DefaultKafkaConsumerFactoryCustomizer;
import org.springframework.boot.kafka.autoconfigure.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.databind.json.JsonMapper;

@AutoConfiguration
@ConditionalOnClass(KafkaTemplate.class)
public class CommonKafkaAutoConfiguration {


    @Bean
    public DefaultKafkaProducerFactoryCustomizer jsonProducerFactoryCustomizer(JsonMapper jsonMapper) {
        JacksonJsonSerializer<Object> serializer = new JacksonJsonSerializer<>(jsonMapper);

        return new DefaultKafkaProducerFactoryCustomizer() {
            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public void customize(DefaultKafkaProducerFactory<?, ?> producerFactory) {
                ((DefaultKafkaProducerFactory) producerFactory).setValueSerializer(serializer);
            }
        };
    }

    @Bean
    public DefaultKafkaConsumerFactoryCustomizer jsonConsumerFactoryCustomizer(JsonMapper jsonMapper) {
        JacksonJsonDeserializer<Object> delegate = new JacksonJsonDeserializer<>(jsonMapper);
        delegate.trustedPackages("com.sajo.*"); // 서비스마다 DTO 패키지가 달라서, 내부 메시지는 전부 신뢰하도록 허용
        // 역직렬화 실패가 poll() 단계에서 바로 터지면 DefaultErrorHandler가 못 잡아서 poison pill이 됨
        // ErrorHandlingDeserializer로 감싸서 실패를 DeserializationException으로 미뤄 리스너 레벨 에러 핸들러가 잡게 함
        ErrorHandlingDeserializer<Object> deserializer = new ErrorHandlingDeserializer<>(delegate);

        return new DefaultKafkaConsumerFactoryCustomizer() {
            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public void customize(DefaultKafkaConsumerFactory<?, ?> consumerFactory) {
                ((DefaultKafkaConsumerFactory) consumerFactory).setValueDeserializer(deserializer);
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(CommonErrorHandler.class)
    public DefaultErrorHandler defaultKafkaErrorHandler(
            @Value("${sajo.kafka.error.retry-interval-ms:1000}") long retryIntervalMs,
            @Value("${sajo.kafka.error.retry-count:3}") long retryCount) {
        // recoverer 없이(null) 재시도만 함 - DLT는 강제 안 함, 필요하면 KafkaErrorHandlers.withDlt(...)로 서비스가 직접 켬
        DefaultErrorHandler handler = new DefaultErrorHandler(new FixedBackOff(retryIntervalMs, retryCount));
        handler.setLogLevel(KafkaException.Level.ERROR);
        // 역직렬화 실패는 재시도해도 매번 똑같이 실패하니 재시도 없이 바로 스킵(로그)하고 넘어감
        handler.addNotRetryableExceptions(DeserializationException.class);
        return handler;
    }
}
