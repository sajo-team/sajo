package com.sajo.operation_service.service;

import com.sajo.operation_service.client.PrometheusQueryResult;
import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest;
import com.sajo.operation_service.service.diagnostics.dependency.DependencyMappingService;
import com.sajo.operation_service.service.diagnostics.host.HostDiagnosticsService;
import com.sajo.operation_service.service.strategy.AlertDiagnosisStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AlertAnalyzer {

    private final HostDiagnosticsService hostDiagnosticsService;
    private final DependencyMappingService dependencyMappingService;
    private final ChatClient chatClient;
    private final Map<String, AlertDiagnosisStrategy> alertnameStrategyRegistry;

    private static final String SYSTEM_PROMPT = """
                너는 SRE 어시스턴트다.
                제공된 알람과 메트릭만 사용하여 다음 형식으로 답해라.
                1. 관찰된 사실
                2. 원인 후보 최대 3개와 확신도(상/중/하)
                3. 다음 확인 항목
                메트릭만으로 확정할 수 없는 원인은 반드시 '후보'라고 표현하세요.
                수치만으로 확정할 수 없는 부분은 추측임을 명시해라.
            """;

    public AlertAnalyzer(
            HostDiagnosticsService hostDiagnosticsService,
            DependencyMappingService dependencyMappingService,
            ChatClient chatClient,
            List<AlertDiagnosisStrategy> strategies
    ) {
        this.hostDiagnosticsService = hostDiagnosticsService;
        this.dependencyMappingService = dependencyMappingService;
        this.chatClient = chatClient;

        Map<String, AlertDiagnosisStrategy> map = new HashMap<>();
        for (AlertDiagnosisStrategy strategy : strategies) {
            for (String alertname : strategy.alertnames()) {
                AlertDiagnosisStrategy previous = map.put(alertname, strategy);
                if (previous != null) {
                    throw new IllegalStateException(
                            "alertname '" + alertname + "'이 두 전략에 중복 등록됨: "
                                    + previous.getClass().getSimpleName() + ", " + strategy.getClass().getSimpleName());
                }
            }
        }
        this.alertnameStrategyRegistry = Map.copyOf(map);
    }

    public Optional<String> analyze(AlertManagerWebhookRequest.Alert alert) {
        String alertname = alert.labels().get("alertname");

        if (alertname == null) {
            log.warn("alertname 라벨이 없는 알람이라 분석을 건너뜁니다. labels={}", alert.labels());
            return Optional.empty();
        }
        String target = alert.labels().get("application");

        AlertDiagnosisStrategy strategy = alertnameStrategyRegistry.get(alertname);
        if (strategy == null) {
            log.info("전략이 아직 없는 alertname이라 분석을 건너뜁니다. alertname={}, application={}", alertname, target);
            return Optional.empty();
        }

        Instant time = alert.startsAt();

        // 1. 알람 종류별 own snapshot - 알람을 실제로 울리게 한 지표
        Map<String, PrometheusQueryResult> metrics = new LinkedHashMap<>();
        metrics.putAll(strategy.diagnose(alert, time));

        // 2. 호스트 스냅샷 - 항상 공통
        metrics.putAll(hostDiagnosticsService.collect(time));

        // 3. 의존관계 스냅샷 - 참고 정보
        if (target != null) {
            metrics.putAll(dependencyMappingService.collect(target, time));
        }

        String userPrompt = createUserPrompt(alert, metrics);
        log.debug("LLM에 보낼 프롬프트. alertname={}\n{}", alertname, userPrompt);

        String response = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt)
                .call()
                .content();

        return Optional.ofNullable(response);
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
