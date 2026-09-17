package com.sajo.operation_service.service;

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

class HostDiagnosticsServiceTest {

    private PrometheusClient prometheusClient;
    private HostDiagnosticsService hostDiagnosticsService;

    @BeforeEach
    void setup() {
        prometheusClient = mock(PrometheusClient.class);
        hostDiagnosticsService = new HostDiagnosticsService(prometheusClient);
    }

    @Test
    @DisplayName("3개 호스트 진단 쿼리(CPU/메모리/디스크)를 모두 조회해서 맵으로 반환한다 - application 파라미터 없음")
    void collect_queriesAllThreeHostMetrics() {
        Instant time = Instant.parse("2026-09-17T03:00:00Z");
        PrometheusQueryResult dummyResult = PrometheusQueryResult.success("query", List.of());

        when(prometheusClient.query(anyString(), eq(time))).thenReturn(dummyResult);

        Map<String, PrometheusQueryResult> metrics = hostDiagnosticsService.collect(time);

        assertThat(metrics).hasSize(3);
        verify(prometheusClient, times(3)).query(anyString(), eq(time));
    }
}
