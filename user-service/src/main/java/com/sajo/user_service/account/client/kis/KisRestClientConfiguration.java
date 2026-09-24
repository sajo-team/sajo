package com.sajo.user_service.account.client.kis;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.autoconfigure.RestClientBuilderConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(KisApiProperties.class)
public class KisRestClientConfiguration {

    @Bean
    RestClient.Builder kisRestClientBuilder(RestClientBuilderConfigurer configurer) {
        // 타임 아웃 설정
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(30));

        // RestClient.builder()로 직접 만들면 Boot 자동설정(계측 등)이 빠져 KIS 호출 span/메트릭이 안 남는다
        return configurer.configure(RestClient.builder()).requestFactory(requestFactory);
    }
}
