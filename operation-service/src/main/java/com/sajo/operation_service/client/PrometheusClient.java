package com.sajo.operation_service.client;

import com.sajo.operation_service.client.dto.response.PrometheusQueryResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
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

    // PromQL 결과가 스칼라 값 하나인 쿼리(p99, CPU 사용률 등)를 지정한 시점(time) 기준으로 평가해서 그 값만 뽑아온다.
    public double queryScalar(String promql, Instant time) {
        // PromQL에 {,},",공백/줄바꿈이 그대로 들어있어서, UriBuilder 람다(DefaultUriBuilderFactory)의
        // 인코딩 모드에 맡기지 않고 UriComponentsBuilder.encode()로 직접 percent-encode한다
        URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/api/v1/query")
                .queryParam("query", promql)
                .queryParam("time", time.getEpochSecond())
                .build()
                .encode()
                .toUri();

        PrometheusQueryResponse response = restClient.get()
                .uri(uri)
                .retrieve()
                .body(PrometheusQueryResponse.class);

        return extractValue(response);
    }

    // 매칭되는 시계열이 없으면(트래픽이 없어서 등) 0으로 처리한다
    private double extractValue(PrometheusQueryResponse response) {
        if (response == null
                || response.data() == null
                || response.data().result().isEmpty()) {
            return 0.0;
        }

        List<Object> value = response.data().result().get(0).value();
        return Double.parseDouble(String.valueOf(value.get(1)));
    }
}
