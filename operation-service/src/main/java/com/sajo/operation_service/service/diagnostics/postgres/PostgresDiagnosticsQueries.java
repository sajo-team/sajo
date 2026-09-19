package com.sajo.operation_service.service.diagnostics.postgres;

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

    // granted/waiting 구분 없이 전체 락 개수를 합산 - 그 자체로는 "블로킹이다"를 확정 못 하지만,
    // transactionRate()와 같이 보면 "트래픽 대비 비정상적으로 많은지" 판단할 근거가 된다.
    static String locksTotal() {
        return "sum(pg_locks_count)";
    }

    // 죽기 직전 트래픽 수준 - locksTotal()을 해석할 맥락을 준다.
    static String transactionRate() {
        return "sum(rate(pg_stat_database_xact_commit[5m]))";
    }
}
