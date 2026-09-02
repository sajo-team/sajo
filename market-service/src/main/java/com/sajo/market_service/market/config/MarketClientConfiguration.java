package com.sajo.market_service.market.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableFeignClients(basePackages = "com.sajo.market_service.market.client.user")
@EnableConfigurationProperties({KisApiProperties.class, MarketQuoteCacheProperties.class})
public class MarketClientConfiguration {

    @Bean
    RestClient.Builder kisRestClientBuilder() {
        return RestClient.builder();
    }
}
