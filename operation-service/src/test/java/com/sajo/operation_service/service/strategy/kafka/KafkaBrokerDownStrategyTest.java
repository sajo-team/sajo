package com.sajo.operation_service.service.strategy.kafka;

import com.sajo.operation_service.client.PrometheusQueryResult;
import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest;
import com.sajo.operation_service.service.diagnostics.kafka.KafkaDiagnosticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaBrokerDownStrategyTest {

    private KafkaDiagnosticsService kafkaDiagnosticsService;
    private KafkaBrokerDownStrategy strategy;

    @BeforeEach
    void setup() {
        kafkaDiagnosticsService = mock(KafkaDiagnosticsService.class);
        strategy = new KafkaBrokerDownStrategy(kafkaDiagnosticsService);
    }

    @Test
    @DisplayName("startsAt이 아니라 2분 전 시점으로 collectForConnectionDown을 호출한다")
    void diagnose_looksBackTwoMinutesBeforeStartsAt() {
        Instant startsAt = Instant.parse("2026-09-19T03:00:00Z");
        Instant expectedLookback = startsAt.minus(Duration.ofMinutes(2));
        AlertManagerWebhookRequest.Alert alert = new AlertManagerWebhookRequest.Alert(
                "firing", Map.of("alertname", "KafkaBrokerDown", "application", "kafka"), Map.of(), startsAt
        );
        Map<String, PrometheusQueryResult> expected = Map.of(
                "Kafka 브로커 수", PrometheusQueryResult.success("query", List.of())
        );
        when(kafkaDiagnosticsService.collectForConnectionDown(expectedLookback)).thenReturn(expected);

        Map<String, PrometheusQueryResult> result = strategy.diagnose(alert, startsAt);

        assertThat(result).isEqualTo(expected);
        verify(kafkaDiagnosticsService).collectForConnectionDown(expectedLookback);
    }
}
