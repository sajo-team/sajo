package com.sajo.operation_service.service.strategy.postgres;
import com.sajo.operation_service.service.strategy.AlertDiagnosisStrategy;
import com.sajo.operation_service.service.strategy.DownAlertLookback;

import com.sajo.operation_service.client.PrometheusQueryResult;
import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest;
import com.sajo.operation_service.service.diagnostics.postgres.PostgresDiagnosticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PostgresConnectionDownStrategy implements AlertDiagnosisStrategy {

    private final PostgresDiagnosticsService postgresDiagnosticsService;

    @Override
    public Map<String, PrometheusQueryResult> diagnose(AlertManagerWebhookRequest.Alert alert, Instant time) {
        return postgresDiagnosticsService.collectForConnectionDown(time.minus(DownAlertLookback.VALUE));
    }
}
