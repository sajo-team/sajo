package com.sajo.operation_service.service.strategy.kafka;
import com.sajo.operation_service.service.strategy.AlertDiagnosisStrategy;
import com.sajo.operation_service.service.strategy.DownAlertLookback;

import com.sajo.operation_service.client.PrometheusQueryResult;
import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest;
import com.sajo.operation_service.service.diagnostics.kafka.KafkaDiagnosticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class KafkaBrokerDownStrategy implements AlertDiagnosisStrategy {

    private final KafkaDiagnosticsService kafkaDiagnosticsService;

    @Override
    public Map<String, PrometheusQueryResult> diagnose(AlertManagerWebhookRequest.Alert alert, Instant time) {
        return kafkaDiagnosticsService.collectForConnectionDown(time.minus(DownAlertLookback.VALUE));
    }
}
