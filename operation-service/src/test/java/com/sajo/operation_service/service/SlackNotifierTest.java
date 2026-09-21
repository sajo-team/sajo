package com.sajo.operation_service.service;

import com.sajo.operation_service.client.SlackClient;
import com.sajo.operation_service.client.dto.request.SlackMessageRequest;
import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest.Alert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SlackNotifierTest {

    private final SlackClient slackClient = mock(SlackClient.class);
    private final SlackNotifier slackNotifier = new SlackNotifier(slackClient);

    private Alert alert(String status, String severity, Instant startsAt, Instant endsAt) {
        return new Alert(
                status,
                Map.of("alertname", "HighCpuUsage", "application", "trading-service", "severity", severity),
                Map.of("summary", "CPU 사용률 95% 초과", "description", "5분간 지속"),
                startsAt,
                endsAt
        );
    }

    private SlackMessageRequest.Attachment captureAttachment() {
        ArgumentCaptor<SlackMessageRequest> captor = ArgumentCaptor.forClass(SlackMessageRequest.class);
        verify(slackClient).send(captor.capture());
        return captor.getValue().attachments().get(0);
    }

    @Test
    @DisplayName("critical 알람은 danger 색상으로, LLM 분석 결과를 포함해 보낸다")
    void notify_critical_sendsDangerColorWithAnalysis() {
        Alert alert = alert("firing", "critical", Instant.now(), Instant.EPOCH);

        slackNotifier.notify(alert, "분석 결과 텍스트");

        SlackMessageRequest.Attachment attachment = captureAttachment();
        assertThat(attachment.color()).isEqualTo("danger");
        assertThat(attachment.text())
                .contains("HighCpuUsage")
                .contains("CPU 사용률 95% 초과")
                .contains("LLM 분석")
                .contains("분석 결과 텍스트");
    }

    @Test
    @DisplayName("warning 알람은 warning 색상으로 보낸다")
    void notify_warning_sendsWarningColor() {
        Alert alert = alert("firing", "warning", Instant.now(), Instant.EPOCH);

        slackNotifier.notify(alert, "분석 결과");

        assertThat(captureAttachment().color()).isEqualTo("warning");
    }

    @Test
    @DisplayName("severity가 critical/warning이 아니면 기본(good) 색상으로 보낸다")
    void notify_unknownSeverity_sendsDefaultColor() {
        Alert alert = alert("firing", "info", Instant.now(), Instant.EPOCH);

        slackNotifier.notify(alert, "분석 결과");

        assertThat(captureAttachment().color()).isEqualTo("good");
    }

    @Test
    @DisplayName("분석 없이 보내면 안내 문구를 포함하고 LLM 분석 섹션은 없다")
    void notifyWithoutAnalysis_sendsFallbackMessage() {
        Alert alert = alert("firing", "critical", Instant.now(), Instant.EPOCH);

        slackNotifier.notifyWithoutAnalysis(alert);

        SlackMessageRequest.Attachment attachment = captureAttachment();
        assertThat(attachment.text())
                .contains("전략 미등록 또는 분석 실패")
                .doesNotContain("*LLM 분석*");
    }

    @Test
    @DisplayName("resolved는 지속시간을 분/초로 계산해서 포함하고 good 색상으로 보낸다")
    void notifyResolved_includesDuration() {
        Alert alert = alert(
                "resolved", "critical",
                Instant.parse("2026-09-20T10:00:00Z"),
                Instant.parse("2026-09-20T10:01:30Z")
        );

        slackNotifier.notifyResolved(alert);

        SlackMessageRequest.Attachment attachment = captureAttachment();
        assertThat(attachment.color()).isEqualTo("good");
        assertThat(attachment.text()).contains("RESOLVED").contains("1분 30초");
    }

    @Test
    @DisplayName("endsAt이 startsAt보다 앞서는 비정상 데이터도 음수 대신 0초로 표시한다")
    void notifyResolved_negativeDuration_showsZero() {
        Alert alert = alert(
                "resolved", "critical",
                Instant.parse("2026-09-20T10:01:00Z"),
                Instant.parse("2026-09-20T10:00:00Z")
        );

        slackNotifier.notifyResolved(alert);

        assertThat(captureAttachment().text()).contains("지속시간: 0초");
    }
}
