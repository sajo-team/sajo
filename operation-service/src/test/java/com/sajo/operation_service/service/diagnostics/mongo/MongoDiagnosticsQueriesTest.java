package com.sajo.operation_service.service.diagnostics.mongo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MongoDiagnosticsQueriesTest {

    @Test
    @DisplayName("up 쿼리는 exporter 전용 mongodb_up 메트릭을 쓴다")
    void up_usesMongodbUpMetric() {
        assertThat(MongoDiagnosticsQueries.up()).isEqualTo("mongodb_up");
    }

    @Test
    @DisplayName("connectionsUsage 쿼리는 rules.yml의 MongoConnectionsHigh와 동일한 공식을 쓴다")
    void connectionsUsage_reusesMongoConnectionsHighFormula() {
        String query = MongoDiagnosticsQueries.connectionsUsage();

        assertThat(query)
                .contains("mongodb_ss_connections")
                .contains("conn_type");
    }
}
