package com.sajo.operation_service.client;

import com.sajo.operation_service.client.dto.request.SlackMessageRequest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
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

        // Slack Incoming Webhook은 URL 경로 자체가 비밀키라 계측에서 제외한다 - 켜두면 경로 전체가
        // http_client_requests의 uri 라벨과 Zipkin span(http.url)에 남고, traceId 헤더도 Slack으로 나간다.
        // 전송 실패 감시는 아래 slack_send_failures_total 카운터로 충분하다.
        this.restClient = restClientBuilder.clone()
                .requestFactory(requestFactory)
                .observationRegistry(ObservationRegistry.NOOP)
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
