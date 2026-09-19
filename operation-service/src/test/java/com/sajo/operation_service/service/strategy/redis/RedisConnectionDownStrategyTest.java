package com.sajo.operation_service.service.strategy.redis;

import com.sajo.operation_service.client.PrometheusQueryResult;
import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest;
import com.sajo.operation_service.service.diagnostics.host.HostDiagnosticsService;
import com.sajo.operation_service.service.diagnostics.redis.RedisDiagnosticsService;
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

class RedisConnectionDownStrategyTest {

    private RedisDiagnosticsService redisDiagnosticsService;
    private HostDiagnosticsService hostDiagnosticsService;
    private RedisConnectionDownStrategy strategy;

    @BeforeEach
    void setup() {
        redisDiagnosticsService = mock(RedisDiagnosticsService.class);
        hostDiagnosticsService = mock(HostDiagnosticsService.class);
        strategy = new RedisConnectionDownStrategy(redisDiagnosticsService, hostDiagnosticsService);
    }

    @Test
    @DisplayName("startsAt이 아니라 2분 전 시점으로 collectForConnectionDown을 호출한다")
    void diagnose_looksBackTwoMinutesBeforeStartsAt() {
        Instant startsAt = Instant.parse("2026-09-19T03:00:00Z");
        Instant expectedLookback = startsAt.minus(Duration.ofMinutes(2));
        AlertManagerWebhookRequest.Alert alert = new AlertManagerWebhookRequest.Alert(
                "firing", Map.of("alertname", "RedisConnectionDown", "application", "redis"), Map.of(), startsAt
        );
        Map<String, PrometheusQueryResult> expected = Map.of(
                "Redis 연결 상태(up)", PrometheusQueryResult.success("query", List.of())
        );
        when(redisDiagnosticsService.collectForConnectionDown(expectedLookback)).thenReturn(expected);
        when(hostDiagnosticsService.collectNetworkForConnectionDown(expectedLookback)).thenReturn(Map.of());

        StrategyDiagnosis result = strategy.diagnose(alert, startsAt);

        assertThat(result.metrics()).isEqualTo(expected);
        assertThat(result.queryTime()).isEqualTo(expectedLookback);
        verify(redisDiagnosticsService).collectForConnectionDown(expectedLookback);
        verify(hostDiagnosticsService).collectNetworkForConnectionDown(expectedLookback);
    }
}
