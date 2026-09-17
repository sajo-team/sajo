package com.sajo.operation_service.client;

import com.sajo.operation_service.client.dto.response.PrometheusApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

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

    private void mockResponse(PrometheusApiResponse response) {
        when(restClient.get().uri(any(URI.class)).retrieve().body(PrometheusApiResponse.class))
                .thenReturn(response);
    }

    @Test
    @DisplayName("정상 응답(status=success)이면 series 목록을 라벨/값으로 올바르게 매핑한다")
    void query_success_mapsSeriesCorrectly() {
        PrometheusApiResponse response = new PrometheusApiResponse(
                "success",
                new PrometheusApiResponse.Data(
                        "vector",
                        List.of(new PrometheusApiResponse.Result(
                                Map.of("application", "trading-service"),
                                List.of(1700000000, "0.42")
                        ))
                ),
                null,
                null
        );
        mockResponse(response);

        PrometheusQueryResult result = prometheusClient.query("process_cpu_usage", Instant.now());

        assertThat(result.successful()).isTrue();
        assertThat(result.series()).hasSize(1);
        assertThat(result.series().get(0).labels()).containsEntry("application", "trading-service");
        assertThat(result.series().get(0).value()).isEqualTo("0.42");
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

    @Test
    @DisplayName("Prometheus가 4xx/5xx로 응답하면(RestClientResponseException) failure 값을 반환한다")
    void query_httpErrorResponse_returnsFailureValue() {
        HttpServerErrorException exception = HttpServerErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                HttpHeaders.EMPTY,
                "boom".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );
        when(restClient.get().uri(any(URI.class)).retrieve().body(PrometheusApiResponse.class))
                .thenThrow(exception);

        PrometheusQueryResult result = prometheusClient.query("up", Instant.now());

        assertThat(result.successful()).isFalse();
        assertThat(result.error()).contains("500").contains("boom");
    }

    @Test
    @DisplayName("Prometheus 응답의 status가 success가 아니면 errorType/error를 담아 failure 값을 반환한다")
    void query_nonSuccessStatus_returnsFailureValue() {
        PrometheusApiResponse response = new PrometheusApiResponse(
                "error", null, "bad_data", "invalid parameter \"query\""
        );
        mockResponse(response);

        PrometheusQueryResult result = prometheusClient.query("invalid{", Instant.now());

        assertThat(result.successful()).isFalse();
        assertThat(result.error()).contains("bad_data").contains("invalid parameter");
    }

    @Test
    @DisplayName("response.data()가 null이면 매칭되는 시계열이 없는 것으로 보고 빈 series의 성공을 반환한다")
    void query_nullData_returnsSuccessWithEmptySeries() {
        PrometheusApiResponse response = new PrometheusApiResponse("success", null, null, null);
        mockResponse(response);

        PrometheusQueryResult result = prometheusClient.query("up", Instant.now());

        assertThat(result.successful()).isTrue();
        assertThat(result.series()).isEmpty();
    }

    @Test
    @DisplayName("response 자체가 null이면 failure 값을 반환한다")
    void query_nullResponse_returnsFailureValue() {
        mockResponse(null);

        PrometheusQueryResult result = prometheusClient.query("up", Instant.now());

        assertThat(result.successful()).isFalse();
        assertThat(result.error()).contains("비어 있습니다");
    }
}
