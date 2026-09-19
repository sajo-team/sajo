package com.sajo.operation_service.service.diagnostics.postgres;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresDiagnosticsQueriesTest {

    @Test
    @DisplayName("up 쿼리는 exporter 전용 pg_up 메트릭을 쓴다")
    void up_usesPgUpMetric() {
        assertThat(PostgresDiagnosticsQueries.up()).isEqualTo("pg_up");
    }

    @Test
    @DisplayName("connectionsUsage 쿼리는 rules.yml의 PostgresConnectionsHigh와 동일한 공식을 쓴다")
    void connectionsUsage_reusesPostgresConnectionsHighFormula() {
        String query = PostgresDiagnosticsQueries.connectionsUsage();

        assertThat(query)
                .contains("pg_stat_database_numbackends")
                .contains("pg_settings_max_connections");
    }
}
