package com.sajo.operation_service.service.app;

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

class DiagnosticsServiceTest {

    private PrometheusClient prometheusClient;
    private DiagnosticsService diagnosticsService;

    @BeforeEach
    void setup() {
        prometheusClient = mock(PrometheusClient.class);
        diagnosticsService = new DiagnosticsService(prometheusClient);
    }

    @Test
    @DisplayName("4개 진단 쿼리(p99/5xx/CPU/Heap)를 모두 조회해서 맵으로 반환한다")
    void collect_queriesAllFourMetrics() {
        Instant time = Instant.parse("2026-09-17T03:00:00Z");
        PrometheusQueryResult dummyResult = PrometheusQueryResult.success("query", List.of());

        when(prometheusClient.query(anyString(), eq(time))).thenReturn(dummyResult);

        Map<String, PrometheusQueryResult> metrics = diagnosticsService.collect("trading-service", time);

        assertThat(metrics).hasSize(4);
        verify(prometheusClient, times(4)).query(anyString(), eq(time));
    }
}
