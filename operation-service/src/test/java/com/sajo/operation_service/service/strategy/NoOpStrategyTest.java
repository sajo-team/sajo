package com.sajo.operation_service.service.strategy;

import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpStrategyTest {

    @Test
    @DisplayName("항상 빈 맵을 반환한다")
    void diagnose_alwaysReturnsEmptyMap() {
        NoOpStrategy strategy = new NoOpStrategy();
        AlertManagerWebhookRequest.Alert alert = new AlertManagerWebhookRequest.Alert(
                "firing", Map.of("alertname", "HighNodeCpuUsage"), Map.of(), Instant.parse("2026-09-18T03:00:00Z")
        );

        StrategyDiagnosis result = strategy.diagnose(alert, alert.startsAt());

        assertThat(result.metrics()).isEmpty();
        assertThat(result.queryTime()).isEqualTo(alert.startsAt());
    }
}
