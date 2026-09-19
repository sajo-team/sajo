package com.sajo.operation_service.service.strategy.redis;

import com.sajo.operation_service.client.PrometheusQueryResult;
import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest;
import com.sajo.operation_service.service.diagnostics.redis.RedisDiagnosticsService;
import com.sajo.operation_service.service.strategy.StrategyDiagnosis;
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

class RedisMemoryHighStrategyTest {

    private RedisDiagnosticsService redisDiagnosticsService;
    private RedisMemoryHighStrategy strategy;

    @BeforeEach
    void setup() {
        redisDiagnosticsService = mock(RedisDiagnosticsService.class);
        strategy = new RedisMemoryHighStrategy(redisDiagnosticsService);
    }

    @Test
    @DisplayName("현재 시점(startsAt)으로 RedisDiagnosticsService를 그대로 위임 호출한다 - lookback 없음")
    void diagnose_delegatesToRedisDiagnosticsServiceAtCurrentTime() {
        Instant time = Instant.parse("2026-09-18T03:00:00Z");
        AlertManagerWebhookRequest.Alert alert = new AlertManagerWebhookRequest.Alert(
                "firing", Map.of("alertname", "RedisMemoryHigh", "application", "redis"), Map.of(), time
        );
        Map<String, PrometheusQueryResult> expected = Map.of(
                "Redis 메모리 사용률(0~1)", PrometheusQueryResult.success("query", List.of())
        );
        when(redisDiagnosticsService.collect(time)).thenReturn(expected);

        StrategyDiagnosis result = strategy.diagnose(alert, time);

        assertThat(result.metrics()).isEqualTo(expected);
        assertThat(result.queryTime()).isEqualTo(time);
        verify(redisDiagnosticsService).collect(time);
    }
}
