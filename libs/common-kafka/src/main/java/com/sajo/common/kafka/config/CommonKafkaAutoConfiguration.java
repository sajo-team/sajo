package com.sajo.common.kafka.config;

import com.sajo.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.kafka.autoconfigure.DefaultKafkaConsumerFactoryCustomizer;
import org.springframework.boot.kafka.autoconfigure.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.mapping.DefaultJacksonJavaTypeMapper;
import org.springframework.kafka.support.mapping.JacksonJavaTypeMapper;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;

@AutoConfiguration
@ConditionalOnClass(KafkaTemplate.class)
@EnableConfigurationProperties(KafkaProperties.class)
public class CommonKafkaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DefaultKafkaProducerFactoryCustomizer jsonProducerFactoryCustomizer(
            JsonMapper jsonMapper,
            KafkaProperties kafkaProperties) {

        JacksonJsonSerializer<Object> serializer = new JacksonJsonSerializer<>(jsonMapper);

        if (!kafkaProperties.getTypeMappings().isEmpty()) {
            serializer.setTypeMapper(buildTypeMapper(kafkaProperties.getTypeMappings()));
        }

        return new DefaultKafkaProducerFactoryCustomizer() {
            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public void customize(DefaultKafkaProducerFactory<?, ?> producerFactory) {
                ((DefaultKafkaProducerFactory) producerFactory).setValueSerializer(serializer);
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public DefaultKafkaConsumerFactoryCustomizer jsonConsumerFactoryCustomizer(
            JsonMapper jsonMapper,
            KafkaProperties kafkaProperties) {

        JacksonJsonDeserializer<Object> delegate = new JacksonJsonDeserializer<>(jsonMapper);
        delegate.trustedPackages("com.sajo.*");

        if (!kafkaProperties.getTypeMappings().isEmpty()) {
            delegate.setTypeMapper(buildTypeMapper(kafkaProperties.getTypeMappings()));
        }

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

    /**
     * alias → FQCN 문자열 맵을 alias → Class로 변환해 TypeMapper를 만든다.
     */
    private DefaultJacksonJavaTypeMapper buildTypeMapper(Map<String, String> mappings) {
        DefaultJacksonJavaTypeMapper typeMapper = new DefaultJacksonJavaTypeMapper();

        Map<String, Class<?>> idClassMapping = new HashMap<>();
        mappings.forEach((id, className) -> {
            try {
                idClassMapping.put(id, Class.forName(className));
            } catch (ClassNotFoundException e) {
                throw new IllegalArgumentException(
                        "Kafka type mapping class not found: " + className, e);
            }
        });

        typeMapper.setIdClassMapping(idClassMapping);
        // TYPE_ID 우선 → 헤더의 논리적 토큰을 보고 매핑 테이블에서 클래스를 찾음
        typeMapper.setTypePrecedence(
                JacksonJavaTypeMapper.TypePrecedence.TYPE_ID);

        return typeMapper;
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
        // BusinessException(도메인 규칙 위반)도 같은 입력이면 재시도해도 결과가 안 바뀌니 바로 스킵
        handler.addNotRetryableExceptions(DeserializationException.class, BusinessException.class);
        return handler;
    }
}
