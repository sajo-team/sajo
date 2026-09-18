package com.sajo.operation_service.service.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedisDiagnosticsQueriesTest {

    @Test
    @DisplayName("up 쿼리는 exporter 전용 redis_up 메트릭을 쓴다")
    void up_usesRedisUpMetric() {
        assertThat(RedisDiagnosticsQueries.up()).isEqualTo("redis_up");
    }

    @Test
    @DisplayName("memoryUsage 쿼리는 rules.yml의 RedisMemoryHigh와 동일한 공식을 쓴다")
    void memoryUsage_reusesRedisMemoryHighFormula() {
        String query = RedisDiagnosticsQueries.memoryUsage();

        assertThat(query)
                .contains("redis_memory_used_bytes")
                .contains("redis_memory_max_bytes");
    }
}
