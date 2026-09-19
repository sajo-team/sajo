package com.sajo.operation_service.service.strategy.app;

import com.sajo.operation_service.client.PrometheusQueryResult;
import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest;
import com.sajo.operation_service.service.diagnostics.app.DiagnosticsService;
import com.sajo.operation_service.service.strategy.StrategyDiagnosis;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AppMetricsStrategyTest {

    private DiagnosticsService diagnosticsService;
    private AppMetricsStrategy strategy;

    @BeforeEach
    void setup() {
        diagnosticsService = mock(DiagnosticsService.class);
        strategy = new AppMetricsStrategy(diagnosticsService);
    }

    private AlertManagerWebhookRequest.Alert createAlert(Map<String, String> labels) {
        return new AlertManagerWebhookRequest.Alert(
                "firing",
                labels,
                Map.of("summary", "요약", "description", "설명"),
                Instant.parse("2026-09-18T03:00:00Z")
        );
    }

    @Test
    @DisplayName("user-service 알람이면 application=user-service로 DiagnosticsService를 호출한다")
    void diagnose_userServiceAlert_collectsWithUserServiceApplication() {
        Instant time = Instant.parse("2026-09-18T03:00:00Z");
        AlertManagerWebhookRequest.Alert alert = createAlert(Map.of(
                "alertname", "HighCpuUsage", "application", "user-service"
        ));
        Map<String, PrometheusQueryResult> metrics = Map.of(
                "CPU 사용률(0~1)", PrometheusQueryResult.success(
                        "process_cpu_usage{application=\"user-service\"}",
                        List.of(new PrometheusQueryResult.Series(Map.of("application", "user-service"), "0.75"))
                )
        );
        when(diagnosticsService.collect("user-service", time)).thenReturn(metrics);

        StrategyDiagnosis result = strategy.diagnose(alert, time);

        assertThat(result.metrics()).isEqualTo(metrics);
        assertThat(result.queryTime()).isEqualTo(time);
    }

    @Test
    @DisplayName("application 라벨이 없으면 DiagnosticsService를 호출하지 않고 예외를 던진다")
    void diagnose_missingApplicationLabel_throwsWithoutQuerying() {
        AlertManagerWebhookRequest.Alert alert = createAlert(Map.of("alertname", "HighCpuUsage"));

        assertThatThrownBy(() -> strategy.diagnose(alert, alert.startsAt()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HighCpuUsage");

        verifyNoInteractions(diagnosticsService);
    }

    @Test
    @DisplayName("application 라벨에 PromQL 인젝션에 쓰일 수 있는 문자가 섞이면 예외를 던진다")
    void diagnose_invalidApplicationLabel_throwsWithoutQuerying() {
        AlertManagerWebhookRequest.Alert alert = createAlert(Map.of(
                "alertname", "HighCpuUsage",
                "application", "user-service\"} or process_cpu_usage{application=\"a"
        ));

        assertThatThrownBy(() -> strategy.diagnose(alert, alert.startsAt()))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(diagnosticsService);
    }
}
