package com.sajo.operation_service.service.strategy.redis;
import com.sajo.operation_service.service.strategy.AlertDiagnosisStrategy;
import com.sajo.operation_service.service.strategy.DownAlertLookback;

import com.sajo.operation_service.client.PrometheusQueryResult;
import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest;
import com.sajo.operation_service.service.AlertNames;
import com.sajo.operation_service.service.diagnostics.redis.RedisDiagnosticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
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
    public Map<String, PrometheusQueryResult> diagnose(AlertManagerWebhookRequest.Alert alert, Instant time) {
        return redisDiagnosticsService.collectForConnectionDown(time.minus(DownAlertLookback.VALUE));
    }
}
