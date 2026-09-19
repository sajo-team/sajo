package com.sajo.operation_service.client;

import com.sajo.operation_service.client.dto.response.PrometheusApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
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

    public PrometheusQueryResult query(String promql, Instant time) {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/api/v1/query")
                .queryParam("query", promql)
                .queryParam("time", time.getEpochSecond())
                .build()
                .encode()
                .toUri();

        // UriComponentsBuilder.encode()는 RFC 3986 기준으로 '+'가 유효한 문자라 인코딩하지 않는데,
        // Prometheus(Go net/url)는 application/x-www-form-urlencoded 관례를 따라 쿼리스트링의 '+'를
        // 공백으로 해석한다.- %2B로 재인코딩해서 방지한다.
        uri = URI.create(uri.toString().replace("+", "%2B"));

        PrometheusApiResponse response;
        try {
            response = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(PrometheusApiResponse.class);
        } catch (RestClientResponseException e) {
            // Prometheus가 4xx/5xx로 응답한 경우
            return PrometheusQueryResult.failure(
                    promql,
                    "HTTP " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString()
            );
        } catch (RestClientException e) {
            // 연결 실패/타임아웃 등 응답 자체를 못 받은 경우 - ResourceAccessException이 여기 해당.

            return PrometheusQueryResult.failure(
                    promql,
                    "Prometheus 연결 실패: " + e.getMessage()
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
