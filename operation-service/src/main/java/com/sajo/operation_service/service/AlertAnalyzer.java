package com.sajo.operation_service.service;

import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest;
import com.sajo.operation_service.service.dto.AppDiagnosticsSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AlertAnalyzer {

    private final DiagnosticsService diagnosticsService;
    private final ChatClient chatClient;

    private static final String SYSTEM_PROMPT = """
                너는 SRE 어시스턴트다.
                제공된 알람과 메트릭만 사용하여 다음 형식으로 답해라.
                1. 관찰된 사실
                2. 원인 후보 최대 3개와 확신도(상/중/하)
                3. 다음 확인 항목
                메트릭만으로 확정할 수 없는 원인은 반드시 '후보'라고 표현하세요.
                수치만으로 확정할 수 없는 부분은 추측임을 명시해라.
            """;

    public String analyze(AlertManagerWebhookRequest.Alert alert) {
        String application = alert.labels().get("application");
        if (application == null) {
            throw new IllegalArgumentException(
                    "application 라벨이 없는 알람. alertname=" + alert.labels().get("alertname"));
        }

        AppDiagnosticsSnapshot snapshot = diagnosticsService.collect(application, alert.startsAt());
        String userPrompt = createUserPrompt(alert, snapshot);

        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt)
                .call()
                .content();
    }

    private String createUserPrompt(AlertManagerWebhookRequest.Alert alert, AppDiagnosticsSnapshot snapshot) {
        return """
                [알람]
                이름: %s
                심각도: %s
                요약: %s
                설명: %s
                발생 시각: %s

                [진단 지표 (발생 시점 기준 조회)]
                p99 지연시간: %.3f초
                5xx 에러율: %.2f%%
                CPU 사용률: %.2f%%
                Heap(Old Gen) 사용률: %.2f%%
                """.formatted(
                alert.labels().get("alertname"),
                alert.labels().get("severity"),
                alert.annotations().get("summary"),
                alert.annotations().get("description"),
                alert.startsAt(),
                snapshot.p99LatencySeconds(),
                snapshot.errorRate() * 100,
                snapshot.cpuUsage() * 100,
                snapshot.heapUsageRatio() * 100
        );
    }
}
