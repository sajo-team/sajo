package com.sajo.operation_service.service.strategy.postgres;

import com.sajo.operation_service.client.PrometheusQueryResult;
import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest;
import com.sajo.operation_service.service.diagnostics.postgres.PostgresDiagnosticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostgresConnectionHighStrategyTest {

    private PostgresDiagnosticsService postgresDiagnosticsService;
    private PostgresConnectionHighStrategy strategy;

    @BeforeEach
    void setup() {
        postgresDiagnosticsService = mock(PostgresDiagnosticsService.class);
        strategy = new PostgresConnectionHighStrategy(postgresDiagnosticsService);
    }

    @Test
    @DisplayName("현재 시점(startsAt)으로 PostgresDiagnosticsService를 그대로 위임 호출한다 - lookback 없음")
    void diagnose_delegatesToPostgresDiagnosticsServiceAtCurrentTime() {
        Instant time = Instant.parse("2026-09-18T03:00:00Z");
        AlertManagerWebhookRequest.Alert alert = new AlertManagerWebhookRequest.Alert(
                "firing", Map.of("alertname", "PostgresConnectionsHigh", "application", "postgres"), Map.of(), time
        );
        Map<String, PrometheusQueryResult> expected = Map.of(
                "Postgres 커넥션 사용률(0~1)", PrometheusQueryResult.success("query", List.of())
        );
        when(postgresDiagnosticsService.collect(time)).thenReturn(expected);

        Map<String, PrometheusQueryResult> result = strategy.diagnose(alert, time);

        assertThat(result).isEqualTo(expected);
        verify(postgresDiagnosticsService).collect(time);
    }
}
