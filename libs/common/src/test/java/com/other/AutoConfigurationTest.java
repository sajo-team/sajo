package com.other;

import com.sajo.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TestApplication.class)
@DisplayName("공통 예외 핸들러 auto-configuration 테스트")
class AutoConfigurationTest {

    @Autowired
    private GlobalExceptionHandler globalExceptionHandler;

    @Test
    @DisplayName("GlobalExceptionHandler가 자동으로 빈 등록된다")
    void globalExceptionHandlerIsAutoConfigured() {
        assertThat(globalExceptionHandler).isNotNull();
    }
}
