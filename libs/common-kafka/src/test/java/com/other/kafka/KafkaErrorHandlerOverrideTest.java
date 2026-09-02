package com.other.kafka;

import com.other.TestApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {TestApplication.class, KafkaErrorHandlerOverrideTest.CustomErrorHandlerConfig.class})
@DisplayName("서비스가 직접 CommonErrorHandler를 등록하면 라이브러리 기본값을 대체하는 테스트")
class KafkaErrorHandlerOverrideTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("서비스 정의 CommonErrorHandler가 라이브러리 기본값 대신 쓰인다")
    void serviceDefinedErrorHandlerOverridesLibraryDefault() {
        assertThat(context.getBeansOfType(CommonErrorHandler.class)).hasSize(1);
        assertThat(context.getBean(CommonErrorHandler.class))
                .isSameAs(context.getBean("customKafkaErrorHandler"));
    }

    @TestConfiguration
    static class CustomErrorHandlerConfig {

        @Bean
        CommonErrorHandler customKafkaErrorHandler() {
            return new DefaultErrorHandler(new FixedBackOff(500L, 1));
        }
    }
}
