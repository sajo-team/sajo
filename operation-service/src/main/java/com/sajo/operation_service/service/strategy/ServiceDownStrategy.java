package com.sajo.operation_service.service.strategy;

import com.sajo.operation_service.client.PrometheusQueryResult;
import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest;
import com.sajo.operation_service.service.app.DiagnosticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

// AppMetricsStrategy와 동일한 DiagnosticsService를 재사용하지만, 서비스가 죽으면 startsAt 시점엔
// 이미 값이 없어서(실측 확인) 별도 클래스로 분리하고 lookback을 적용한다.
@Component
@RequiredArgsConstructor
public class ServiceDownStrategy implements AlertDiagnosisStrategy {

    private final DiagnosticsService diagnosticsService;

    @Override
    public Map<String, PrometheusQueryResult> diagnose(AlertManagerWebhookRequest.Alert alert, Instant time) {
        String application = ApplicationLabels.require(alert);
        return diagnosticsService.collect(application, time.minus(DownAlertLookback.VALUE));
    }
}
