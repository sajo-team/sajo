package com.other.kafka;

import com.sajo.common.exception.BusinessException;
import com.sajo.common.kafka.config.KafkaErrorHandlers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("KafkaErrorHandlers.withDlt 재시도 제외 분류 테스트")
class KafkaErrorHandlersTest {

    @Test
    @DisplayName("역직렬화 실패와 BusinessException은 재시도 없이 바로 DLT로 보내지도록 분류된다")
    void deserializationAndBusinessExceptionAreNotRetryable() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<Object, Object> template = mock(KafkaTemplate.class);
        DefaultErrorHandler handler = KafkaErrorHandlers.withDlt(template);

        // addNotRetryableExceptions로 등록된 항목은 false로 분류되어 있음 - removeClassification이
        // 그 분류값을 반환하므로, 등록 여부를 이 반환값으로 검증한다
        assertThat(handler.removeClassification(DeserializationException.class)).isFalse();
        assertThat(handler.removeClassification(BusinessException.class)).isFalse();
    }
}
