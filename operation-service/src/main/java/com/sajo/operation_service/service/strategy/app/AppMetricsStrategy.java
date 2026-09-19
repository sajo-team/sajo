package com.sajo.operation_service.service.strategy.app;
import com.sajo.operation_service.service.AlertNames;
import com.sajo.operation_service.service.strategy.AlertDiagnosisStrategy;
import com.sajo.operation_service.service.strategy.StrategyDiagnosis;

import com.sajo.operation_service.client.PrometheusQueryResult;
import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest;
import com.sajo.operation_service.service.diagnostics.app.DiagnosticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

// user-service/market-service/trading-service 공통 - 알람의 application 라벨로 어느 서비스인지
// 판단해서 기존 DiagnosticsService(1단계에서 작성됨)를 그대로 재사용한다. 서비스마다 별도 클래스가
// 필요 없다: application 값만 다를 뿐 조회 로직 자체는 동일하기 때문.
// ServiceDown처럼 "다운"류 알람은 이 전략이 아니라 별도 전략(직전 시점 조회)을 써야 한다 - 서비스가
// 죽으면 이 전략이 조회하는 4개 지표가 전부 0으로 나와 의미가 없다(1단계에서 실측 확인됨).
@Component
@RequiredArgsConstructor
public class AppMetricsStrategy implements AlertDiagnosisStrategy {

    private final DiagnosticsService diagnosticsService;

    @Override
    public Set<String> alertnames() {
        return Set.of(
                AlertNames.HIGH_ERROR_RATE,
                AlertNames.HIGH_LATENCY,
                AlertNames.HIGH_CPU_USAGE,
                AlertNames.HIGH_MEMORY_USAGE,
                AlertNames.HIGH_GC_OVERHEAD,
                AlertNames.HIKARI_POOL_PENDING
        );
    }

    @Override
    public StrategyDiagnosis diagnose(AlertManagerWebhookRequest.Alert alert, Instant time) {
        String application = ApplicationLabels.require(alert);
        return new StrategyDiagnosis(time, diagnosticsService.collect(application, time));
    }
}
