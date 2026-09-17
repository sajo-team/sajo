package com.sajo.operation_service.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Configuration
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    // Spring AI의 OpenAiChatAutoConfiguration이 이 RestClient.Builder를 ObjectProvider로 그대로
    // 주입받아 쓰므로, 여기서 타임아웃을 걸어두면 OpenAI 호출에도 적용된다. PrometheusClient는 자기
    // build 시점에 requestFactory를 직접 다시 지정하므로 이 커스터마이저에 영향받지 않는다.
    @Bean
    public RestClientCustomizer openAiRestClientCustomizer(
            @Value("${sajo.openai.rest-client.connect-timeout-ms:5000}") int connectTimeoutMillis,
            @Value("${sajo.openai.rest-client.read-timeout-ms:90000}") int readTimeoutMillis
    ) {
        return builder -> {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(connectTimeoutMillis);
            requestFactory.setReadTimeout(readTimeoutMillis);
            builder.requestFactory(requestFactory);
        };
    }
}
