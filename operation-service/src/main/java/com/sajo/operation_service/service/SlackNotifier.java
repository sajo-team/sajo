package com.sajo.operation_service.service;

import com.sajo.operation_service.client.SlackClient;
import com.sajo.operation_service.client.dto.request.SlackMessageRequest;
import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest.Alert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class SlackNotifier {

    private static final String COLOR_CRITICAL = "danger";
    private static final String COLOR_WARNING = "warning";
    private static final String COLOR_DEFAULT = "good";

    private final SlackClient slackClient;

    public void notify(Alert alert, String analysis) {
        String color = severityColor(alert.labels().get("severity"));
        String message = formatMessage(alert, analysis);
        slackClient.send(SlackMessageRequest.of(color, message));
    }

    public void notifyResolved(Alert alert) {
        String message = formatResolvedMessage(alert);
        slackClient.send(SlackMessageRequest.of(COLOR_DEFAULT, message));
    }

    // 전략 미등록 또는 LLM 분석 실패 시에도 알람 자체는 원본 정보로라도 전달한다 -
    // slack_configs 제거 후 "분석 안 되면 Slack에 아예 안 뜸"이 되는 회귀를 막기 위함.
    public void notifyWithoutAnalysis(Alert alert) {
        String color = severityColor(alert.labels().get("severity"));
        String message = formatMessageWithoutAnalysis(alert);
        slackClient.send(SlackMessageRequest.of(color, message));
    }

    private String severityColor(String severity) {
        if ("critical".equalsIgnoreCase(severity)) {
            return COLOR_CRITICAL;
        }
        if ("warning".equalsIgnoreCase(severity)) {
            return COLOR_WARNING;
        }
        return COLOR_DEFAULT;
    }

    private String formatResolvedMessage(Alert alert) {
        Duration duration = Duration.between(alert.startsAt(), alert.endsAt());
        return """
                :white_check_mark: *[RESOLVED][%s] %s* (`%s`)
                지속시간: %s
                """.formatted(
                alert.labels().get("alertname"),
                alert.annotations().get("summary"),
                alert.labels().get("application"),
                formatDuration(duration)
        );
    }

    private String formatDuration(Duration duration) {

        if (duration.isNegative()) {
            duration = Duration.ZERO;
        }
        long minutes = duration.toMinutes();
        long seconds = duration.minusMinutes(minutes).getSeconds();
        if (minutes > 0) {
            return "%d분 %d초".formatted(minutes, seconds);
        }
        return "%d초".formatted(seconds);
    }

    private String formatMessage(Alert alert, String analysis) {
        return """
                %s *[%s] %s* (`%s`)
                %s

                *LLM 분석*
                
                %s
                """.formatted(
                severityEmoji(alert.labels().get("severity")),
                alert.labels().get("alertname"),
                alert.annotations().get("summary"),
                alert.labels().get("application"),
                alert.annotations().get("description"),
                analysis
        );
    }

    private String formatMessageWithoutAnalysis(Alert alert) {
        return """
                %s *[%s] %s* (`%s`)
                %s

                _LLM 분석 없음 - 전략 미등록 또는 분석 실패_
                """.formatted(
                severityEmoji(alert.labels().get("severity")),
                alert.labels().get("alertname"),
                alert.annotations().get("summary"),
                alert.labels().get("application"),
                alert.annotations().get("description")
        );
    }

    private String severityEmoji(String severity) {
        if ("critical".equalsIgnoreCase(severity)) {
            return ":red_circle:";
        }
        if ("warning".equalsIgnoreCase(severity)) {
            return ":large_yellow_circle:";
        }
        return ":white_circle:";
    }
}
