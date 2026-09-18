package com.sajo.operation_service.service;

import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertAnalysisAsyncProcessorTest {

    private final AlertAnalyzer alertAnalyzer = mock(AlertAnalyzer.class);
    private final AlertAnalysisAsyncProcessor processor = new AlertAnalysisAsyncProcessor(alertAnalyzer);

    private AlertManagerWebhookRequest.Alert createAlert(String status, String alertname) {
        return new AlertManagerWebhookRequest.Alert(
                status,
                Map.of("alertname", alertname, "application", "trading-service"),
                Map.of(),
                Instant.parse("2026-09-17T03:00:00Z")
        );
    }

    @Test
    @DisplayName("firing 상태인 알람만 분석한다")
    void process_onlyAnalyzesFiringAlerts() {
        AlertManagerWebhookRequest.Alert firing = createAlert("firing", "HighCpuUsage");
        AlertManagerWebhookRequest.Alert resolved = createAlert("resolved", "HighCpuUsage");

        when(alertAnalyzer.analyze(any())).thenReturn("분석 결과");

        processor.process(new AlertManagerWebhookRequest("firing", List.of(firing, resolved)));

        verify(alertAnalyzer, times(1)).analyze(any());
        verify(alertAnalyzer).analyze(firing);
    }

    @Test
    @DisplayName("알람 하나 분석이 실패해도 나머지 알람은 계속 처리한다")
    void process_oneFailure_doesNotStopBatch() {
        AlertManagerWebhookRequest.Alert first = createAlert("firing", "HighCpuUsage");
        AlertManagerWebhookRequest.Alert second = createAlert("firing", "HighErrorRate");

        when(alertAnalyzer.analyze(first)).thenThrow(new RuntimeException("Prometheus 실패"));
        when(alertAnalyzer.analyze(second)).thenReturn("분석 결과");

        processor.process(new AlertManagerWebhookRequest("firing", List.of(first, second)));

        verify(alertAnalyzer).analyze(first);
        verify(alertAnalyzer).analyze(second);
    }
}
