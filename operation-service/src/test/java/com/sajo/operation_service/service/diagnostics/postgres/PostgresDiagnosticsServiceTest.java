package com.sajo.operation_service.service.diagnostics.postgres;

import com.sajo.operation_service.client.PrometheusClient;
import com.sajo.operation_service.client.PrometheusQueryResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostgresDiagnosticsServiceTest {

    private PrometheusClient prometheusClient;
    private PostgresDiagnosticsService postgresDiagnosticsService;

    @BeforeEach
    void setup() {
        prometheusClient = mock(PrometheusClient.class);
        postgresDiagnosticsService = new PostgresDiagnosticsService(prometheusClient);
    }

    @Test
    @DisplayName("up 상태와 커넥션 사용률 2개 쿼리를 모두 조회해서 맵으로 반환한다")
    void collect_queriesUpAndConnectionsUsage() {
        Instant time = Instant.parse("2026-09-18T03:00:00Z");
        when(prometheusClient.query(anyString(), eq(time)))
                .thenReturn(PrometheusQueryResult.success("query", List.of()));

        Map<String, PrometheusQueryResult> metrics = postgresDiagnosticsService.collect(time);

        assertThat(metrics).hasSize(2);
        verify(prometheusClient, times(2)).query(anyString(), eq(time));
    }
}
