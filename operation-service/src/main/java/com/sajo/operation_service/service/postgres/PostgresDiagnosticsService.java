package com.sajo.operation_service.service.postgres;

import com.sajo.operation_service.client.PrometheusClient;
import com.sajo.operation_service.client.PrometheusQueryResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

// Postgres 자체(알람 컨텍스트 없이)의 기본 상태 - DependencyMappingService가 관련 대상 조회에 쓰고,
// 나중에 Postgres 관련 알람 전용 Strategy가 생기면 그 안에서도 재사용한다.
@Service
@RequiredArgsConstructor
public class PostgresDiagnosticsService {

    private final PrometheusClient prometheusClient;

    public Map<String, PrometheusQueryResult> collect(Instant time) {
        Map<String, PrometheusQueryResult> metrics = new LinkedHashMap<>();

        metrics.put("Postgres 연결 상태(up)", prometheusClient.query(PostgresDiagnosticsQueries.up(), time));
        metrics.put("Postgres 커넥션 사용률(0~1)", prometheusClient.query(PostgresDiagnosticsQueries.connectionsUsage(), time));

        return metrics;
    }
}
