package com.sajo.trading_service.trading.client;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

class StrategyClientConfigTest {

    private final StrategyClientConfig strategyClientConfig = new StrategyClientConfig();

    @Test
    @DisplayName("내부 API 호출 시 X-Internal-Secret 헤더가 추가된다")
    void addInternalSecretHeader() {
        // given
        String internalApiSecret = "test-internal-secret";
        RequestInterceptor interceptor =
                strategyClientConfig.internalApiRequestInterceptor(internalApiSecret);

        RequestTemplate requestTemplate = new RequestTemplate();

        // when
        interceptor.apply(requestTemplate);

        // then
        Collection<String> headerValues =
                requestTemplate.headers().get("X-Internal-Secret");

        assertThat(headerValues)
                .isNotNull()
                .containsExactly(internalApiSecret);
    }
}