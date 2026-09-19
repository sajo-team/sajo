package com.sajo.operation_service.service.diagnostics.mongo;

// MongoDiagnosticsService가 쓰는 PromQL 조립만 담당.
final class MongoDiagnosticsQueries {

    private MongoDiagnosticsQueries() {
    }

    // mongo-exporter는 스크랩되고 있어도(generic up==1) mongod 연결은 끊길 수 있어서
    // rules.yml의 MongoConnectionDown과 동일하게 exporter 전용 up 메트릭을 쓴다.
    static String up() {
        return "mongodb_up";
    }

    // rules.yml의 MongoConnectionsHigh와 동일한 공식 - conn_type 라벨값이 달라 집계로 제거해야 매칭됨
    static String connectionsUsage() {
        return """
                sum without (conn_type) (mongodb_ss_connections{conn_type="current"})
                /
                sum without (conn_type) (mongodb_ss_connections{conn_type=~"current|available"})
                """;
    }

    // 죽기 직전 트래픽 수준(query/insert/update/delete 등 op 종류 무관하게 합산)
    static String opRate() {
        return "sum(rate(mongodb_ss_opcounters[5m]))";
    }
}
