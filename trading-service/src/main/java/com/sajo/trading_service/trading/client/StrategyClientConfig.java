package com.sajo.trading_service.trading.client;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

public class StrategyClientConfig {

    @Bean
    public RequestInterceptor internalApiRequestInterceptor(
            @Value("${sajo.internal.api-secret}") String internalApiSecret
    ) {
        return requestTemplate ->
                requestTemplate.header("X-Internal-Secret", internalApiSecret);
    }
}