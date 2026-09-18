package com.sajo.operation_service.service.postgres;

// PostgresDiagnosticsService가 쓰는 PromQL 조립만 담당.
final class PostgresDiagnosticsQueries {

    private PostgresDiagnosticsQueries() {
    }

    // postgres-exporter 프로세스는 스크랩돼도(generic up==1) postgres 자체 연결은 끊길 수 있어서
    // rules.yml의 PostgresConnectionDown과 동일하게 exporter 전용 up 메트릭을 쓴다.
    static String up() {
        return "pg_up";
    }

    // rules.yml의 PostgresConnectionsHigh와 동일한 공식
    static String connectionsUsage() {
        return """
                sum(pg_stat_database_numbackends) / scalar(pg_settings_max_connections)
                """;
    }
}
