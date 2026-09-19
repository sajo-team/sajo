package com.sajo.operation_service.service.diagnostics.redis;

// RedisDiagnosticsService가 쓰는 PromQL 조립만 담당.
final class RedisDiagnosticsQueries {

    private RedisDiagnosticsQueries() {
    }

    // redis-exporter 프로세스는 스크랩돼도(generic up==1) redis 자체 연결은 끊길 수 있어서
    // rules.yml의 RedisConnectionDown과 동일하게 exporter 전용 up 메트릭을 쓴다.
    static String up() {
        return "redis_up";
    }

    // rules.yml의 RedisMemoryHigh와 동일한 공식
    static String memoryUsage() {
        return """
                redis_memory_used_bytes / (redis_memory_max_bytes > 0)
                """;
    }

    // 1=성공, 0=실패. 실패면 디스크 공간 부족/fork 실패(메모리 부족) 등 죽기 전 자원 문제의 신호.
    // 이 프로젝트 Redis는 appendonly=no라 AOF 상태는 의미 없어서 RDB만 본다(실측 확인).
    static String rdbLastSaveStatus() {
        return "redis_rdb_last_bgsave_status";
    }

    // 죽기 직전 트래픽 수준 - 다른 지표(연결끊김, 메모리 등)를 해석할 때 "바빴는지" 맥락을 준다.
    static String commandRate() {
        return "rate(redis_commands_processed_total[5m])";
    }
}
