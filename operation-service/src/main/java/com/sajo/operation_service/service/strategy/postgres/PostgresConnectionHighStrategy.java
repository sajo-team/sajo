package com.sajo.operation_service.service.strategy.postgres;
import com.sajo.operation_service.service.strategy.AlertDiagnosisStrategy;
import com.sajo.operation_service.service.strategy.StrategyDiagnosis;

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
public class PostgresConnectionHighStrategy implements AlertDiagnosisStrategy{

    private final PostgresDiagnosticsService postgresDiagnosticsService;

    @Override
    public Set<String> alertnames() {
        return Set.of(AlertNames.POSTGRES_CONNECTIONS_HIGH);
    }

    @Override
    public StrategyDiagnosis diagnose(AlertManagerWebhookRequest.Alert alert, Instant time) {
        return new StrategyDiagnosis(time, postgresDiagnosticsService.collect(time));
    }
}
