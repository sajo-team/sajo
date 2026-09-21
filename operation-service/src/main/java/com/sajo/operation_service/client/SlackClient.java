package com.sajo.operation_service.client;

import com.sajo.operation_service.client.dto.request.SlackMessageRequest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
public class SlackClient {

    private final RestClient restClient;
    private final String webhookUrl;
    private final Counter slackSendFailureCounter;

    public SlackClient(
            RestClient.Builder restClientBuilder,
            MeterRegistry meterRegistry,
            @Value("${sajo.slack.webhook-url}") String webhookUrl,
            @Value("${sajo.slack.rest-client.connect-timeout-ms:2000}") int connectTimeoutMillis,
            @Value("${sajo.slack.rest-client.read-timeout-ms:5000}") int readTimeoutMillis
    ) {
        this.webhookUrl = webhookUrl;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMillis);
        requestFactory.setReadTimeout(readTimeoutMillis);

        this.restClient = restClientBuilder.clone()
                .requestFactory(requestFactory)
                .build();

        this.slackSendFailureCounter = Counter.builder("slack_send_failures_total")
                .description("Slack 알림 전송 실패 횟수")
                .register(meterRegistry);
    }

    public void send(SlackMessageRequest request) {
        try {
            restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            slackSendFailureCounter.increment();
            log.error("Slack 전송 실패 - HTTP {}: {}", e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (RestClientException e) {
            slackSendFailureCounter.increment();
            log.error("Slack 연결 실패", e);
        }
    }
}
