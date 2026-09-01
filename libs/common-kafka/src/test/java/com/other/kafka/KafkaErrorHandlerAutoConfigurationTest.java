package com.other.kafka;

import com.other.TestApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;

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
}
