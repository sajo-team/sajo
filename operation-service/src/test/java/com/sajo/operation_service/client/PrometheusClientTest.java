package com.sajo.operation_service.client;

import com.sajo.operation_service.client.dto.response.PrometheusApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PrometheusClientTest {

    private RestClient restClient;
    private PrometheusClient prometheusClient;

    @BeforeEach
    void setup() {
        restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
        RestClient.Builder restClientBuilder = mock(RestClient.Builder.class, RETURNS_DEEP_STUBS);

        when(restClientBuilder.clone()).thenReturn(restClientBuilder);
        when(restClientBuilder.baseUrl(anyString())).thenReturn(restClientBuilder);
        when(restClientBuilder.requestFactory(any())).thenReturn(restClientBuilder);
        when(restClientBuilder.build()).thenReturn(restClient);

        prometheusClient = new PrometheusClient(restClientBuilder, "http://localhost:9090", 2000, 5000);
    }

    @Test
    @DisplayName("Prometheus 연결 자체가 안 되면(ResourceAccessException) 예외 대신 failure 값을 반환한다")
    void query_connectionFailure_returnsFailureValue() {
        when(restClient.get().uri(any(URI.class)).retrieve().body(PrometheusApiResponse.class))
                .thenThrow(new ResourceAccessException("Connection timed out"));

        PrometheusQueryResult result = prometheusClient.query("up", Instant.now());

        assertThat(result.successful()).isFalse();
        assertThat(result.error()).contains("Connection timed out");
    }
}
