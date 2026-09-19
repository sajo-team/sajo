package com.sajo.operation_service.service.strategy;

import com.sajo.operation_service.client.PrometheusQueryResult;
import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest;
import com.sajo.operation_service.service.app.DiagnosticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ServiceDownStrategyTest {

    private DiagnosticsService diagnosticsService;
    private ServiceDownStrategy strategy;

    @BeforeEach
    void setup() {
        diagnosticsService = mock(DiagnosticsService.class);
        strategy = new ServiceDownStrategy(diagnosticsService);
    }

    @Test
    @DisplayName("startsAt이 아니라 2분 전 시점으로 application 기준 DiagnosticsService를 호출한다")
    void diagnose_looksBackTwoMinutesBeforeStartsAt() {
        Instant startsAt = Instant.parse("2026-09-19T03:00:00Z");
        Instant expectedLookback = startsAt.minus(Duration.ofMinutes(2));
        AlertManagerWebhookRequest.Alert alert = new AlertManagerWebhookRequest.Alert(
                "firing", Map.of("alertname", "ServiceDown", "application", "trading-service"), Map.of(), startsAt
        );
        Map<String, PrometheusQueryResult> expected = Map.of(
                "CPU 사용률(0~1)", PrometheusQueryResult.success("query", List.of())
        );
        when(diagnosticsService.collect("trading-service", expectedLookback)).thenReturn(expected);

        Map<String, PrometheusQueryResult> result = strategy.diagnose(alert, startsAt);

        assertThat(result).isEqualTo(expected);
        verify(diagnosticsService).collect("trading-service", expectedLookback);
    }

    @Test
    @DisplayName("application 라벨이 없으면 DiagnosticsService를 호출하지 않고 예외를 던진다")
    void diagnose_missingApplicationLabel_throwsWithoutQuerying() {
        Instant startsAt = Instant.parse("2026-09-19T03:00:00Z");
        AlertManagerWebhookRequest.Alert alert = new AlertManagerWebhookRequest.Alert(
                "firing", Map.of("alertname", "ServiceDown"), Map.of(), startsAt
        );

        assertThatThrownBy(() -> strategy.diagnose(alert, startsAt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ServiceDown");

        verifyNoInteractions(diagnosticsService);
    }
}
