package com.sajo.operation_service.service.strategy.mongo;

import com.sajo.operation_service.client.PrometheusQueryResult;
import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest;
import com.sajo.operation_service.service.diagnostics.host.HostDiagnosticsService;
import com.sajo.operation_service.service.diagnostics.mongo.MongoDiagnosticsService;
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

class MongoConnectionDownStrategyTest {

    private MongoDiagnosticsService mongoDiagnosticsService;
    private HostDiagnosticsService hostDiagnosticsService;
    private MongoConnectionDownStrategy strategy;

    @BeforeEach
    void setup() {
        mongoDiagnosticsService = mock(MongoDiagnosticsService.class);
        hostDiagnosticsService = mock(HostDiagnosticsService.class);
        strategy = new MongoConnectionDownStrategy(mongoDiagnosticsService, hostDiagnosticsService);
    }

    @Test
    @DisplayName("startsAt이 아니라 2분 전 시점으로 collectForConnectionDown을 호출한다")
    void diagnose_looksBackTwoMinutesBeforeStartsAt() {
        Instant startsAt = Instant.parse("2026-09-19T03:00:00Z");
        Instant expectedLookback = startsAt.minus(Duration.ofMinutes(2));
        AlertManagerWebhookRequest.Alert alert = new AlertManagerWebhookRequest.Alert(
                "firing", Map.of("alertname", "MongoConnectionDown", "application", "mongo"), Map.of(), startsAt
        );
        Map<String, PrometheusQueryResult> expected = Map.of(
                "MongoDB 연결 상태(up)", PrometheusQueryResult.success("query", List.of())
        );
        when(mongoDiagnosticsService.collectForConnectionDown(expectedLookback)).thenReturn(expected);
        when(hostDiagnosticsService.collectNetworkForConnectionDown(expectedLookback)).thenReturn(Map.of());

        StrategyDiagnosis result = strategy.diagnose(alert, startsAt);

        assertThat(result.metrics()).isEqualTo(expected);
        assertThat(result.queryTime()).isEqualTo(expectedLookback);
        verify(mongoDiagnosticsService).collectForConnectionDown(expectedLookback);
        verify(hostDiagnosticsService).collectNetworkForConnectionDown(expectedLookback);
    }
}
