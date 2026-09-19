package com.sajo.operation_service.service.diagnostics.kafka;

import com.sajo.operation_service.client.PrometheusClient;
import com.sajo.operation_service.client.PrometheusQueryResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaDiagnosticsServiceTest {

    private PrometheusClient prometheusClient;
    private KafkaDiagnosticsService kafkaDiagnosticsService;

    @BeforeEach
    void setup() {
        prometheusClient = mock(PrometheusClient.class);
        kafkaDiagnosticsService = new KafkaDiagnosticsService(prometheusClient);
    }

    @Test
    @DisplayName("브로커 수 쿼리 1개를 조회해서 맵으로 반환한다")
    void collect_queriesBrokerCount() {
        Instant time = Instant.parse("2026-09-18T03:00:00Z");
        when(prometheusClient.query(eq("kafka_brokers"), eq(time)))
                .thenReturn(PrometheusQueryResult.success("kafka_brokers", List.of()));

        Map<String, PrometheusQueryResult> metrics = kafkaDiagnosticsService.collect(time);

        assertThat(metrics).hasSize(1);
        verify(prometheusClient).query(eq("kafka_brokers"), eq(time));
    }
}
