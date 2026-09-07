package com.other.jwt;

import com.other.TestApplication;
import com.sajo.common.jwt.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TestApplication.class)
@DisplayName("JWT 컴포넌트 auto-configuration 테스트")
class JwtAutoConfigurationTest {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("JwtTokenProvider가 자동으로 빈 등록된다")
    void jwtTokenProviderIsAutoConfigured() {
        assertThat(jwtTokenProvider).isNotNull();
    }
}
