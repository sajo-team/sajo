package com.sajo.operation_service.service;

import com.sajo.operation_service.client.PrometheusQueryResult;
import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest;
import com.sajo.operation_service.service.diagnostics.dependency.DependencyMappingService;
import com.sajo.operation_service.service.diagnostics.host.HostDiagnosticsService;
import com.sajo.operation_service.service.strategy.AlertDiagnosisStrategy;
import com.sajo.operation_service.service.strategy.StrategyDiagnosis;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class AlertAnalyzer {

    private final HostDiagnosticsService hostDiagnosticsService;
    private final DependencyMappingService dependencyMappingService;
    private final ChatClient chatClient;
    private final Map<String, AlertDiagnosisStrategy> alertnameStrategyRegistry;

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

        // 1. 알람 종류별 own snapshot - 알람을 실제로 울리게 한 지표. lookback을 적용하는 전략은
        // 실제 조회 시각이 time과 다를 수 있어서(diagnosis.queryTime()), 아래 2/3과 분리해서 다룬다.
        StrategyDiagnosis diagnosis = strategy.diagnose(alert, time);

        // 2. 호스트 스냅샷 - 항상 공통, 항상 time(발생 시각) 기준
        Map<String, PrometheusQueryResult> hostAndDependencyMetrics = new LinkedHashMap<>();
        hostAndDependencyMetrics.putAll(hostDiagnosticsService.collect(time));

        // 3. 의존관계 스냅샷 - 참고 정보, 항상 time(발생 시각) 기준
        if (target != null) {
            hostAndDependencyMetrics.putAll(dependencyMappingService.collect(target, time));
        }

        String userPrompt = AlertPromptBuilder.userPrompt(alert, diagnosis, hostAndDependencyMetrics);
        log.debug("LLM에 보낼 프롬프트. alertname={}\n{}", alertname, userPrompt);

        String response = chatClient.prompt()
                .system(AlertPromptBuilder.SYSTEM_PROMPT)
                .user(userPrompt)
                .call()
                .content();

        return Optional.ofNullable(response);
    }
}
