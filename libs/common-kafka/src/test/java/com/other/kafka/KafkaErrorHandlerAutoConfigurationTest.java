package com.other.kafka;

import com.other.TestApplication;
import com.sajo.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TestApplication.class)
@DisplayName("Kafka 에러 핸들러 기본값 자동 등록 테스트")
class KafkaErrorHandlerAutoConfigurationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("서비스가 CommonErrorHandler를 안 만들면 라이브러리 기본값(재시도만, DLT 없음)이 등록된다")
    void defaultErrorHandlerIsRegisteredWhenServiceDefinesNone() {
        assertThat(context.getBeansOfType(CommonErrorHandler.class)).hasSize(1);
        assertThat(context.getBean(CommonErrorHandler.class)).isInstanceOf(DefaultErrorHandler.class);
    }

    @Test
    @DisplayName("역직렬화 실패와 BusinessException은 재시도 없이 바로 스킵되도록 분류된다")
    void deserializationAndBusinessExceptionAreNotRetryable() {
        DefaultErrorHandler handler = (DefaultErrorHandler) context.getBean(CommonErrorHandler.class);

        // addNotRetryableExceptions로 등록된 항목은 false로 분류되어 있음 - removeClassification이
        // 그 분류값을 반환하므로, 등록 여부를 이 반환값으로 검증한다
        assertThat(handler.removeClassification(DeserializationException.class)).isFalse();
        assertThat(handler.removeClassification(BusinessException.class)).isFalse();
    }
}
