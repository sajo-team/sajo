package com.sajo.operation_service.service.strategy.postgres;
import com.sajo.operation_service.service.strategy.AlertDiagnosisStrategy;
import com.sajo.operation_service.service.strategy.StrategyDiagnosis;
import com.sajo.operation_service.service.strategy.DownAlertLookback;

import com.sajo.operation_service.client.PrometheusQueryResult;
import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest;
import com.sajo.operation_service.service.AlertNames;
import com.sajo.operation_service.service.diagnostics.postgres.PostgresDiagnosticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class PostgresConnectionDownStrategy implements AlertDiagnosisStrategy {

    private final PostgresDiagnosticsService postgresDiagnosticsService;

    @Override
    public Set<String> alertnames() {
        return Set.of(AlertNames.POSTGRES_CONNECTION_DOWN);
    }

    @Override
    public StrategyDiagnosis diagnose(AlertManagerWebhookRequest.Alert alert, Instant time) {
        Instant queryTime = time.minus(DownAlertLookback.VALUE);
        return new StrategyDiagnosis(queryTime, postgresDiagnosticsService.collectForConnectionDown(queryTime));
    }
}
