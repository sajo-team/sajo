package com.sajo.market_service.market.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.autoconfigure.RestClientBuilderConfigurer;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@EnableFeignClients(basePackages = "com.sajo.market_service.market.client.user")
@EnableConfigurationProperties({KisApiProperties.class, MarketQuoteCacheProperties.class})
public class MarketClientConfiguration {

    @Bean
    RestClient.Builder kisRestClientBuilder(RestClientBuilderConfigurer configurer) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        // RestClient.builder()로 직접 만들면 Boot 자동설정(계측 등)이 빠져 KIS 호출 span/메트릭이 안 남는다
        return configurer.configure(RestClient.builder()).requestFactory(requestFactory);
    }
}
