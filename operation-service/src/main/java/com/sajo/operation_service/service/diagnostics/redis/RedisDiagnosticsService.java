package com.sajo.operation_service.service.diagnostics.redis;

import com.sajo.operation_service.client.PrometheusClient;
import com.sajo.operation_service.client.PrometheusQueryResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

// Redis 자체(알람 컨텍스트 없이)의 기본 상태 - DependencyMappingService가 관련 대상 조회에 쓰고,
// 나중에 Redis 관련 알람(RedisConnectionDown 등) 전용 Strategy가 생기면 그 안에서도 재사용한다.
@Service
@RequiredArgsConstructor
public class RedisDiagnosticsService {

    private final PrometheusClient prometheusClient;

    public Map<String, PrometheusQueryResult> collect(Instant time) {
        Map<String, PrometheusQueryResult> metrics = new LinkedHashMap<>();

        metrics.put("Redis 연결 상태(up)", prometheusClient.query(RedisDiagnosticsQueries.up(), time));
        metrics.put("Redis 메모리 사용률(0~1)", prometheusClient.query(RedisDiagnosticsQueries.memoryUsage(), time));

        return metrics;
    }

    // RedisConnectionDown 전용 - collect()에 없는 2개(RDB 저장 상태/트래픽)를 더 본다.
    public Map<String, PrometheusQueryResult> collectForConnectionDown(Instant time) {
        Map<String, PrometheusQueryResult> metrics = new LinkedHashMap<>(collect(time));

        metrics.put("RDB 마지막 저장 상태(1=성공/0=실패)", prometheusClient.query(RedisDiagnosticsQueries.rdbLastSaveStatus(), time));
        metrics.put("초당 명령 처리량", prometheusClient.query(RedisDiagnosticsQueries.commandRate(), time));

        return metrics;
    }
}
