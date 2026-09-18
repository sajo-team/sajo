package com.sajo.operation_service.service.strategy;

import com.sajo.operation_service.client.PrometheusQueryResult;
import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest;
import com.sajo.operation_service.service.postgres.PostgresDiagnosticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PostgresConnectionHighStrategy implements AlertDiagnosisStrategy{

    private final PostgresDiagnosticsService postgresDiagnosticsService;

    @Override
    public Map<String, PrometheusQueryResult> diagnose(AlertManagerWebhookRequest.Alert alert, Instant time) {
        return postgresDiagnosticsService.collect(time);
    }
}
