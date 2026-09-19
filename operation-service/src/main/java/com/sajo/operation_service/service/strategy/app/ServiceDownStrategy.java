package com.sajo.operation_service.service.strategy.app;

import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest;
import com.sajo.operation_service.service.AlertNames;
import com.sajo.operation_service.service.diagnostics.app.DiagnosticsService;
import com.sajo.operation_service.service.strategy.AlertDiagnosisStrategy;
import com.sajo.operation_service.service.strategy.DownAlertLookback;
import com.sajo.operation_service.service.strategy.StrategyDiagnosis;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;

// AppMetricsStrategy와 동일한 DiagnosticsService를 재사용하지만, 서비스가 죽으면 startsAt 시점엔
// 이미 값이 없어서(실측 확인) 별도 클래스로 분리하고 lookback을 적용한다.
@Component
@RequiredArgsConstructor
public class ServiceDownStrategy implements AlertDiagnosisStrategy {

    private final DiagnosticsService diagnosticsService;

    @Override
    public Set<String> alertnames() {
        return Set.of(AlertNames.SERVICE_DOWN);
    }

    @Override
    public StrategyDiagnosis diagnose(AlertManagerWebhookRequest.Alert alert, Instant time) {
        String application = ApplicationLabels.require(alert);
        Instant queryTime = time.minus(DownAlertLookback.VALUE);
        return new StrategyDiagnosis(queryTime, diagnosticsService.collect(application, queryTime));
    }
}
