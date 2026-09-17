package com.sajo.operation_service.service;

import com.sajo.operation_service.client.PrometheusQueryResult;
import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AlertAnalyzer {

    // application 라벨은 서비스명(예: trading-service)이라 영문자/숫자/하이픈만 허용 -
    // PromQL 템플릿에 그대로 꽂혀 들어가므로("{application=\"%s\"}") 이 값에 ", {, } 등이
    // 섞이면 PromQL 인젝션이 될 수 있음
    private static final Pattern APPLICATION_LABEL_PATTERN = Pattern.compile("^[a-zA-Z0-9-]+$");

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

        if (!APPLICATION_LABEL_PATTERN.matcher(application).matches()) {
            throw new IllegalArgumentException(
                    "application 라벨 형식이 올바르지 않은 알람. application=" + application);
        }

        Map<String, PrometheusQueryResult> metrics = diagnosticsService.collect(application, alert.startsAt());
        String userPrompt = createUserPrompt(alert, metrics);

        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt)
                .call()
                .content();
    }

    private String createUserPrompt(AlertManagerWebhookRequest.Alert alert, Map<String, PrometheusQueryResult> metrics) {
        String metricsText = metrics.entrySet().stream()
                .map(entry -> "[" + entry.getKey() + "]\n" + entry.getValue().toPromptText())
                .collect(Collectors.joining("\n\n"));

        return """
                [알람]
                이름: %s
                심각도: %s
                요약: %s
                설명: %s
                발생 시각: %s

                [진단 지표 (발생 시점 기준 조회)]
                %s
                """.formatted(
                alert.labels().get("alertname"),
                alert.labels().get("severity"),
                alert.annotations().get("summary"),
                alert.annotations().get("description"),
                alert.startsAt(),
                metricsText
        );
    }
}
