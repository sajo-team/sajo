package com.sajo.operation_service.service.strategy.redis;
import com.sajo.operation_service.service.strategy.AlertDiagnosisStrategy;

import com.sajo.operation_service.client.PrometheusQueryResult;
import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest;
import com.sajo.operation_service.service.AlertNames;
import com.sajo.operation_service.service.diagnostics.redis.RedisDiagnosticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

@RequiredArgsConstructor
@Component
public class RedisMemoryHighStrategy implements AlertDiagnosisStrategy{

    private final RedisDiagnosticsService redisDiagnosticsService;

    @Override
    public Set<String> alertnames() {
        return Set.of(AlertNames.REDIS_MEMORY_HIGH);
    }

    @Override
    public Map<String, PrometheusQueryResult> diagnose(AlertManagerWebhookRequest.Alert alert, Instant time) {
        return redisDiagnosticsService.collect(time);
    }
}
