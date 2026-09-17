package com.sajo.operation_service.client;

import com.sajo.operation_service.client.dto.response.PrometheusApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.List;

@Component
public class PrometheusClient {

    private final RestClient restClient;
    private final String baseUrl;

    public PrometheusClient(
            RestClient.Builder restClientBuilder,
            @Value("${sajo.prometheus.base-url}") String baseUrl,
            @Value("${sajo.prometheus.rest-client.connect-timeout-ms:2000}") int connectTimeoutMillis,
            @Value("${sajo.prometheus.rest-client.read-timeout-ms:5000}") int readTimeoutMillis
    ) {
        this.baseUrl = baseUrl;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMillis);
        requestFactory.setReadTimeout(readTimeoutMillis);

        this.restClient = restClientBuilder.clone()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    // PromQL을 지정한 시점(time) 기준으로 평가한다. 연결 자체가 안 되는 경우(타임아웃 등)는
    // RestClientException을 그대로 던지고, Prometheus가 응답은 했지만 실패인 경우만
    // PrometheusQueryResult.failure로 값으로 표현한다 (0.0으로 뭉개지 않기 위함)
    public PrometheusQueryResult query(String promql, Instant time) {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/api/v1/query")
                .queryParam("query", promql)
                .queryParam("time", time.getEpochSecond())
                .build()
                .encode()
                .toUri();

        PrometheusApiResponse response;
        try {
            response = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(PrometheusApiResponse.class);
        } catch (RestClientResponseException e) {
            return PrometheusQueryResult.failure(
                    promql,
                    "HTTP " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString()
            );
        }

        if (response == null) {
            return PrometheusQueryResult.failure(promql, "Prometheus 응답이 비어 있습니다.");
        }
        if (!"success".equals(response.status())) {
            return PrometheusQueryResult.failure(promql, response.errorType() + ": " + response.error());
        }
        if (response.data() == null || response.data().result() == null) {
            return PrometheusQueryResult.success(promql, List.of());
        }

        List<PrometheusQueryResult.Series> series = response.data().result().stream()
                .map(result -> new PrometheusQueryResult.Series(
                        result.metric(),
                        String.valueOf(result.value().get(1))
                ))
                .toList();

        return PrometheusQueryResult.success(promql, series);
    }
}
