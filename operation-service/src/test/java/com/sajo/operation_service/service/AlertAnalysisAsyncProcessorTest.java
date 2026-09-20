package com.sajo.operation_service.service;

import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertAnalysisAsyncProcessorTest {

    private final AlertAnalyzer alertAnalyzer = mock(AlertAnalyzer.class);
    private final SlackNotifier slackNotifier = mock(SlackNotifier.class);
    private final AlertAnalysisAsyncProcessor processor =
            new AlertAnalysisAsyncProcessor(alertAnalyzer, slackNotifier);

    private AlertManagerWebhookRequest.Alert createAlert(String status, String alertname) {
        return new AlertManagerWebhookRequest.Alert(
                status,
                Map.of("alertname", alertname, "application", "trading-service"),
                Map.of(),
                Instant.parse("2026-09-17T03:00:00Z"),
                Instant.parse("2026-09-17T03:05:00Z")
        );
    }

    @Test
    @DisplayName("firing 알람은 LLM 분석 후 Slack 발송하고, resolved 알람은 분석 없이 복구 알림만 보낸다")
    void process_firingAnalyzes_resolvedNotifiesOnly() {
        AlertManagerWebhookRequest.Alert firing = createAlert("firing", "HighCpuUsage");
        AlertManagerWebhookRequest.Alert resolved = createAlert("resolved", "HighCpuUsage");

        when(alertAnalyzer.analyze(any())).thenReturn(Optional.of("분석 결과"));

        processor.process(new AlertManagerWebhookRequest("firing", List.of(firing, resolved)));

        verify(alertAnalyzer, times(1)).analyze(any());
        verify(alertAnalyzer).analyze(firing);
        verify(slackNotifier).notify(firing, "분석 결과");
        verify(slackNotifier).notifyResolved(resolved);
        verify(slackNotifier, never()).notify(eq(resolved), anyString());
    }

    @Test
    @DisplayName("전략 미등록 등으로 분석이 없으면 raw 알림(notifyWithoutAnalysis)을 보낸다 - Slack에 아예 안 뜨는 회귀 방지")
    void process_noAnalysis_notifiesWithoutAnalysis() {
        AlertManagerWebhookRequest.Alert firing = createAlert("firing", "UnknownAlert");

        when(alertAnalyzer.analyze(firing)).thenReturn(Optional.empty());

        processor.process(new AlertManagerWebhookRequest("firing", List.of(firing)));

        verify(slackNotifier).notifyWithoutAnalysis(firing);
        verify(slackNotifier, never()).notify(eq(firing), anyString());
    }

    @Test
    @DisplayName("전략 유무와 무관하게 resolved 알람은 항상 복구 알림을 보낸다")
    void process_resolved_alwaysNotifies() {
        AlertManagerWebhookRequest.Alert resolved = createAlert("resolved", "UnknownAlert");

        processor.process(new AlertManagerWebhookRequest("firing", List.of(resolved)));

        verify(slackNotifier).notifyResolved(resolved);
    }

    @Test
    @DisplayName("알람 하나 분석이 실패해도 나머지 알람은 계속 처리하고, 실패한 알람도 raw 알림을 보낸다")
    void process_oneFailure_doesNotStopBatch() {
        AlertManagerWebhookRequest.Alert first = createAlert("firing", "HighCpuUsage");
        AlertManagerWebhookRequest.Alert second = createAlert("firing", "HighErrorRate");

        when(alertAnalyzer.analyze(first)).thenThrow(new RuntimeException("Prometheus 실패"));
        when(alertAnalyzer.analyze(second)).thenReturn(Optional.of("분석 결과"));

        processor.process(new AlertManagerWebhookRequest("firing", List.of(first, second)));

        verify(alertAnalyzer).analyze(first);
        verify(alertAnalyzer).analyze(second);
        verify(slackNotifier).notifyWithoutAnalysis(first);
        verify(slackNotifier).notify(second, "분석 결과");
    }

    @Test
    @DisplayName("Slack 발송(notify) 중 예상 못한 예외가 나도 나머지 알람은 계속 처리한다")
    void process_notifyThrows_doesNotStopBatch() {
        AlertManagerWebhookRequest.Alert first = createAlert("firing", "HighCpuUsage");
        AlertManagerWebhookRequest.Alert second = createAlert("firing", "HighErrorRate");

        when(alertAnalyzer.analyze(any())).thenReturn(Optional.of("분석 결과"));
        doThrow(new RuntimeException("잘못된 webhook URL 설정 등 예상 못한 예외"))
                .when(slackNotifier).notify(eq(first), anyString());

        processor.process(new AlertManagerWebhookRequest("firing", List.of(first, second)));

        verify(slackNotifier).notify(second, "분석 결과");
    }

    @Test
    @DisplayName("resolved 알림 발송 중 예상 못한 예외가 나도 나머지 알람은 계속 처리한다")
    void process_notifyResolvedThrows_doesNotStopBatch() {
        AlertManagerWebhookRequest.Alert resolved = createAlert("resolved", "HighCpuUsage");
        AlertManagerWebhookRequest.Alert firing = createAlert("firing", "HighErrorRate");

        doThrow(new RuntimeException("잘못된 webhook URL 설정 등 예상 못한 예외"))
                .when(slackNotifier).notifyResolved(resolved);
        when(alertAnalyzer.analyze(firing)).thenReturn(Optional.of("분석 결과"));

        processor.process(new AlertManagerWebhookRequest("firing", List.of(resolved, firing)));

        verify(slackNotifier).notify(firing, "분석 결과");
    }
}
