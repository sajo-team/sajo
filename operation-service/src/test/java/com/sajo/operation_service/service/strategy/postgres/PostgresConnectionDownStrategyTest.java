package com.sajo.operation_service.service.strategy.postgres;

import com.sajo.operation_service.client.PrometheusQueryResult;
import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest;
import com.sajo.operation_service.service.diagnostics.host.HostDiagnosticsService;
import com.sajo.operation_service.service.diagnostics.postgres.PostgresDiagnosticsService;
import com.sajo.operation_service.service.strategy.StrategyDiagnosis;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostgresConnectionDownStrategyTest {

    private PostgresDiagnosticsService postgresDiagnosticsService;
    private HostDiagnosticsService hostDiagnosticsService;
    private PostgresConnectionDownStrategy strategy;

    @BeforeEach
    void setup() {
        postgresDiagnosticsService = mock(PostgresDiagnosticsService.class);
        hostDiagnosticsService = mock(HostDiagnosticsService.class);
        strategy = new PostgresConnectionDownStrategy(postgresDiagnosticsService, hostDiagnosticsService);
    }

    @Test
    @DisplayName("startsAt이 아니라 2분 전 시점으로 collectForConnectionDown을 호출한다")
    void diagnose_looksBackTwoMinutesBeforeStartsAt() {
        Instant startsAt = Instant.parse("2026-09-19T03:00:00Z");
        Instant expectedLookback = startsAt.minus(Duration.ofMinutes(2));
        AlertManagerWebhookRequest.Alert alert = new AlertManagerWebhookRequest.Alert(
                "firing", Map.of("alertname", "PostgresConnectionDown", "application", "postgres"), Map.of(), startsAt
        );
        Map<String, PrometheusQueryResult> expected = Map.of(
                "Postgres 연결 상태(up)", PrometheusQueryResult.success("query", List.of())
        );
        when(postgresDiagnosticsService.collectForConnectionDown(expectedLookback)).thenReturn(expected);
        when(hostDiagnosticsService.collectNetworkForConnectionDown(expectedLookback)).thenReturn(Map.of());

        StrategyDiagnosis result = strategy.diagnose(alert, startsAt);

        assertThat(result.metrics()).isEqualTo(expected);
        assertThat(result.queryTime()).isEqualTo(expectedLookback);
        verify(postgresDiagnosticsService).collectForConnectionDown(expectedLookback);
        verify(hostDiagnosticsService).collectNetworkForConnectionDown(expectedLookback);
    }
}
