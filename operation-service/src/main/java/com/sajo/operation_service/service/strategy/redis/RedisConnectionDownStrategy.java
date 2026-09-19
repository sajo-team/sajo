package com.sajo.operation_service.service.strategy.redis;

import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest;
import com.sajo.operation_service.service.AlertNames;
import com.sajo.operation_service.service.diagnostics.redis.RedisDiagnosticsService;
import com.sajo.operation_service.service.strategy.AlertDiagnosisStrategy;
import com.sajo.operation_service.service.strategy.DownAlertLookback;
import com.sajo.operation_service.service.strategy.StrategyDiagnosis;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class RedisConnectionDownStrategy implements AlertDiagnosisStrategy {

    private final RedisDiagnosticsService redisDiagnosticsService;

    @Override
    public Set<String> alertnames() {
        return Set.of(AlertNames.REDIS_CONNECTION_DOWN);
    }

    @Override
    public StrategyDiagnosis diagnose(AlertManagerWebhookRequest.Alert alert, Instant time) {
        Instant queryTime = time.minus(DownAlertLookback.VALUE);
        return new StrategyDiagnosis(queryTime, redisDiagnosticsService.collectForConnectionDown(queryTime));
    }
}
