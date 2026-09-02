package com.other.kafka;

import com.other.kafka.dto.ConsumerOrderCreatedEvent;
import com.other.kafka.dto.ProducerOrderCreatedEvent;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.mapping.DefaultJacksonJavaTypeMapper;
import org.springframework.kafka.support.mapping.JacksonJavaTypeMapper;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Producer/Consumer가 서로 다른 DTO 클래스를 쓰는 경우의 직렬화/역직렬화 테스트")
class CrossServiceDtoSerializationTest {

    private static final String EVENT_ALIAS = "order.created";

    @Test
    @DisplayName("type-mapping 없이 보내면 Producer 클래스명이 헤더에 FQCN 그대로 실리고, Consumer가 신뢰하지 않는 패키지면 역직렬화가 거부된다")
    void withoutTypeMapping_untrustedProducerClassIsRejected() {
        JacksonJsonSerializer<Object> serializer = new JacksonJsonSerializer<>(); // addTypeInfo 기본값 true

        Headers headers = new RecordHeaders();
        byte[] bytes = serializer.serialize("orders", headers,
                new ProducerOrderCreatedEvent("ORDER-1", Instant.now().truncatedTo(ChronoUnit.MILLIS)));

        JacksonJsonDeserializer<Object> deserializer = new JacksonJsonDeserializer<>();
        deserializer.trustedPackages("com.sajo.*");

        assertThatThrownBy(() -> deserializer.deserialize("orders", headers, bytes))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not in the trusted packages");
    }

    @Test
    @DisplayName("sajo.kafka.type-mappings로 alias를 맞추면 Producer/Consumer가 서로 다른 클래스를 써도 정상적으로 왕복한다")
    void withTypeMapping_differentDtoClassesRoundTripSuccessfully() {
        // Producer 쪽: 자기 이벤트 클래스를 alias("order.created")에 매핑
        DefaultJacksonJavaTypeMapper producerTypeMapper = new DefaultJacksonJavaTypeMapper();
        producerTypeMapper.setIdClassMapping(Map.of(EVENT_ALIAS, ProducerOrderCreatedEvent.class));
        producerTypeMapper.setTypePrecedence(JacksonJavaTypeMapper.TypePrecedence.TYPE_ID);

        JacksonJsonSerializer<Object> serializer = new JacksonJsonSerializer<>();
        serializer.setTypeMapper(producerTypeMapper);

        Headers headers = new RecordHeaders();
        Instant occurredAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        byte[] bytes = serializer.serialize("orders", headers,
                new ProducerOrderCreatedEvent("ORDER-1", occurredAt));

        // 헤더엔 Producer의 FQCN이 아니라 alias만 실린다 - 클래스명이 와이어에 노출되지 않음
        assertThat(new String(headers.lastHeader("__TypeId__").value(), StandardCharsets.UTF_8))
                .isEqualTo(EVENT_ALIAS);

        // Consumer 쪽: Producer 클래스는 몰라도 됨 - 완전히 다른 자기 클래스를 같은 alias에 매핑
        DefaultJacksonJavaTypeMapper consumerTypeMapper = new DefaultJacksonJavaTypeMapper();
        consumerTypeMapper.setIdClassMapping(Map.of(EVENT_ALIAS, ConsumerOrderCreatedEvent.class));
        consumerTypeMapper.setTypePrecedence(JacksonJavaTypeMapper.TypePrecedence.TYPE_ID);

        JacksonJsonDeserializer<Object> deserializer = new JacksonJsonDeserializer<>();
        deserializer.setTypeMapper(consumerTypeMapper);

        Object received = deserializer.deserialize("orders", headers, bytes);

        assertThat(received).isInstanceOf(ConsumerOrderCreatedEvent.class);
        assertThat((ConsumerOrderCreatedEvent) received)
                .isEqualTo(new ConsumerOrderCreatedEvent("ORDER-1", occurredAt));
    }
}
