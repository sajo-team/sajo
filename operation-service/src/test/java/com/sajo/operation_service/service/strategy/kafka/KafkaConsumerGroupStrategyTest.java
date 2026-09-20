package com.sajo.operation_service.service.strategy.kafka;

import com.sajo.operation_service.client.PrometheusQueryResult;
import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest;
import com.sajo.operation_service.service.diagnostics.kafka.KafkaDiagnosticsService;
import com.sajo.operation_service.service.strategy.StrategyDiagnosis;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaConsumerGroupStrategyTest {

    private KafkaDiagnosticsService kafkaDiagnosticsService;
    private KafkaConsumerGroupStrategy strategy;

    @BeforeEach
    void setup() {
        kafkaDiagnosticsService = mock(KafkaDiagnosticsService.class);
        strategy = new KafkaConsumerGroupStrategy(kafkaDiagnosticsService);
    }

    private AlertManagerWebhookRequest.Alert alertWithLabels(String alertname, Map<String, String> extraLabels, Instant startsAt) {
        Map<String, String> labels = new java.util.LinkedHashMap<>(extraLabels);
        labels.put("alertname", alertname);
        return new AlertManagerWebhookRequest.Alert("firing", labels, Map.of(), startsAt, startsAt);
    }

    @Test
    @DisplayName("ConsumerStalled: consumergroup/topic 둘 다 있으면 lookback 없이 현재 시점으로 조회한다")
    void diagnose_consumerStalled_bothLabels_noLookback() {
        Instant startsAt = Instant.parse("2026-09-19T03:00:00Z");
        AlertManagerWebhookRequest.Alert alert = alertWithLabels(
                "ConsumerStalled",
                Map.of("consumergroup", "trading-consumer-group", "topic", "trading.signal.generated"),
                startsAt
        );
        Map<String, PrometheusQueryResult> expected = Map.of("k", PrometheusQueryResult.success("q", List.of()));
        when(kafkaDiagnosticsService.collectForConsumerGroup("trading-consumer-group", "trading.signal.generated", startsAt))
                .thenReturn(expected);

        StrategyDiagnosis result = strategy.diagnose(alert, startsAt);

        assertThat(result.metrics()).isEqualTo(expected);
        assertThat(result.queryTime()).isEqualTo(startsAt);
        verify(kafkaDiagnosticsService).collectForConsumerGroup("trading-consumer-group", "trading.signal.generated", startsAt);
    }

    @Test
    @DisplayName("ConsumerNoMembers: consumergroup만 있고 topic은 없다")
    void diagnose_consumerNoMembers_onlyConsumergroup() {
        Instant startsAt = Instant.parse("2026-09-19T03:00:00Z");
        AlertManagerWebhookRequest.Alert alert = alertWithLabels(
                "ConsumerNoMembers", Map.of("consumergroup", "market-consumer-group"), startsAt
        );

        strategy.diagnose(alert, startsAt);

        verify(kafkaDiagnosticsService).collectForConsumerGroup("market-consumer-group", null, startsAt);
    }

    @Test
    @DisplayName("MessageDeadLettered: topic만 있고 consumergroup은 없다")
    void diagnose_messageDeadLettered_onlyTopic() {
        Instant startsAt = Instant.parse("2026-09-19T03:00:00Z");
        AlertManagerWebhookRequest.Alert alert = alertWithLabels(
                "MessageDeadLettered", Map.of("topic", "trading.signal.generated.DLT"), startsAt
        );

        strategy.diagnose(alert, startsAt);

        verify(kafkaDiagnosticsService).collectForConsumerGroup(null, "trading.signal.generated.DLT", startsAt);
    }

    @Test
    @DisplayName("ConsumerGroupMissing: 그룹이 사라진 알람이라 12분 lookback을 적용한다 " +
            "(absent()의 lookback_delta 5분 + for 5분 지연을 감안한 값)")
    void diagnose_consumerGroupMissing_appliesLookback() {
        Instant startsAt = Instant.parse("2026-09-19T03:00:00Z");
        Instant expectedLookback = startsAt.minus(Duration.ofMinutes(12));
        AlertManagerWebhookRequest.Alert alert = alertWithLabels(
                "ConsumerGroupMissing", Map.of("consumergroup", "trading-consumer-group"), startsAt
        );

        StrategyDiagnosis result = strategy.diagnose(alert, startsAt);

        assertThat(result.queryTime()).isEqualTo(expectedLookback);
        verify(kafkaDiagnosticsService).collectForConsumerGroup("trading-consumer-group", null, expectedLookback);
    }

    @Test
    @DisplayName("consumergroup 라벨 형식이 PromQL 인젝션 가능한 문자를 포함하면 예외가 발생한다")
    void diagnose_invalidConsumergroupFormat_throws() {
        Instant startsAt = Instant.parse("2026-09-19T03:00:00Z");
        AlertManagerWebhookRequest.Alert alert = alertWithLabels(
                "ConsumerStalled", Map.of("consumergroup", "trading\"} or 1==1 {\""), startsAt
        );

        assertThatThrownBy(() -> strategy.diagnose(alert, startsAt))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("consumergroup/topic 라벨이 둘 다 없으면 조용히 빈 결과를 내지 않고 예외를 던진다")
    void diagnose_bothLabelsMissing_throws() {
        Instant startsAt = Instant.parse("2026-09-19T03:00:00Z");
        AlertManagerWebhookRequest.Alert alert = alertWithLabels("ConsumerStalled", Map.of(), startsAt);

        assertThatThrownBy(() -> strategy.diagnose(alert, startsAt))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
