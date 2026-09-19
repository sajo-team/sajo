package com.sajo.operation_service.service.strategy.mongo;

import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest;
import com.sajo.operation_service.service.AlertNames;
import com.sajo.operation_service.service.diagnostics.mongo.MongoDiagnosticsService;
import com.sajo.operation_service.service.strategy.AlertDiagnosisStrategy;
import com.sajo.operation_service.service.strategy.DownAlertLookback;
import com.sajo.operation_service.service.strategy.StrategyDiagnosis;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class MongoConnectionDownStrategy implements AlertDiagnosisStrategy {

    private final MongoDiagnosticsService mongoDiagnosticsService;

    @Override
    public Set<String> alertnames() {
        return Set.of(AlertNames.MONGO_CONNECTION_DOWN);
    }

    @Override
    public StrategyDiagnosis diagnose(AlertManagerWebhookRequest.Alert alert, Instant time) {
        Instant queryTime = time.minus(DownAlertLookback.VALUE);
        return new StrategyDiagnosis(queryTime, mongoDiagnosticsService.collectForConnectionDown(queryTime));
    }
}
