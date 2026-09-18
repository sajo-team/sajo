package com.sajo.operation_service.service.redis;

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
}
