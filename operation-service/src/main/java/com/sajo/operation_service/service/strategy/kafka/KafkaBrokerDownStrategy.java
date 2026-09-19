package com.sajo.operation_service.service.strategy.kafka;

import com.sajo.operation_service.client.PrometheusQueryResult;
import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest;
import com.sajo.operation_service.service.AlertNames;
import com.sajo.operation_service.service.diagnostics.host.HostDiagnosticsService;
import com.sajo.operation_service.service.diagnostics.kafka.KafkaDiagnosticsService;
import com.sajo.operation_service.service.strategy.AlertDiagnosisStrategy;
import com.sajo.operation_service.service.strategy.DownAlertLookback;
import com.sajo.operation_service.service.strategy.StrategyDiagnosis;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class KafkaBrokerDownStrategy implements AlertDiagnosisStrategy {

    private final KafkaDiagnosticsService kafkaDiagnosticsService;
    private final HostDiagnosticsService hostDiagnosticsService;

    @Override
    public Set<String> alertnames() {
        return Set.of(AlertNames.KAFKA_BROKER_DOWN);
    }

    @Override
    public StrategyDiagnosis diagnose(AlertManagerWebhookRequest.Alert alert, Instant time) {
        Instant queryTime = time.minus(DownAlertLookback.VALUE);
        Map<String, PrometheusQueryResult> metrics = new LinkedHashMap<>(kafkaDiagnosticsService.collectForConnectionDown(queryTime));
        metrics.putAll(hostDiagnosticsService.collectNetworkForConnectionDown(queryTime));
        return new StrategyDiagnosis(queryTime, metrics);
    }
}
