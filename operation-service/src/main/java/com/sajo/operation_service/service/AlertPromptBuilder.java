package com.sajo.operation_service.service;

import com.sajo.operation_service.client.PrometheusQueryResult;
import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest;
import com.sajo.operation_service.service.strategy.StrategyDiagnosis;

import java.util.Map;
import java.util.stream.Collectors;

// LLM에 보낼 프롬프트 조립만 담당 - AlertAnalyzer는 프롬프트 문구를 몰라도 된다
final class AlertPromptBuilder {

    private AlertPromptBuilder() {
    }

    static final String SYSTEM_PROMPT = """
                너는 SRE 어시스턴트다.
                제공된 알람과 메트릭만 사용하여 다음 형식으로 답해라.
                1. 관찰된 사실
                2. 원인 후보 최대 3개와 확신도(상/중/하)
                3. 다음 확인 항목
                메트릭만으로 확정할 수 없는 원인은 반드시 '후보'라고 표현하세요.
                수치만으로 확정할 수 없는 부분은 추측임을 명시해라.
            """;

    static String userPrompt(
            AlertManagerWebhookRequest.Alert alert,
            StrategyDiagnosis diagnosis,
            Map<String, PrometheusQueryResult> hostAndDependencyMetrics
    ) {
        return """
                [알람]
                이름: %s
                심각도: %s
                요약: %s
                설명: %s
                발생 시각: %s

                [알람 자체 진단 지표 (조회 시각: %s)]
                %s

                [호스트/의존관계 스냅샷 (조회 시각: %s, 알람 발생 시각과 동일)]
                %s
                """.formatted(
                alert.labels().get("alertname"),
                alert.labels().get("severity"),
                alert.annotations().get("summary"),
                alert.annotations().get("description"),
                alert.startsAt(),
                diagnosis.queryTime(),
                formatMetrics(diagnosis.metrics()),
                alert.startsAt(),
                formatMetrics(hostAndDependencyMetrics)
        );
    }

    private static String formatMetrics(Map<String, PrometheusQueryResult> metrics) {
        return metrics.entrySet().stream()
                .map(entry -> "[" + entry.getKey() + "]\n" + entry.getValue().toPromptText())
                .collect(Collectors.joining("\n\n"));
    }
}
