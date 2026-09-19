package com.sajo.operation_service.service.diagnostics.kafka;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaDiagnosticsQueriesTest {

    @Test
    @DisplayName("brokerCount 쿼리는 rules.yml의 KafkaBrokerDown과 동일한 메트릭을 쓴다")
    void brokerCount_usesKafkaBrokersMetric() {
        assertThat(KafkaDiagnosticsQueries.brokerCount()).isEqualTo("kafka_brokers");
    }
}
