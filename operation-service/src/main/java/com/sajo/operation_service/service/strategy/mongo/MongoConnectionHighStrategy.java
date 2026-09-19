package com.sajo.operation_service.service.strategy.mongo;
import com.sajo.operation_service.service.strategy.AlertDiagnosisStrategy;

import com.sajo.operation_service.client.PrometheusQueryResult;
import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest;
import com.sajo.operation_service.service.diagnostics.mongo.MongoDiagnosticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MongoConnectionHighStrategy implements AlertDiagnosisStrategy{

    private final MongoDiagnosticsService mongoDiagnosticsService;

    @Override
    public Map<String, PrometheusQueryResult> diagnose(AlertManagerWebhookRequest.Alert alert, Instant time) {
        return mongoDiagnosticsService.collect(time);
    }
}
