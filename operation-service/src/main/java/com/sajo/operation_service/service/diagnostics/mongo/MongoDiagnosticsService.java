package com.sajo.operation_service.service.diagnostics.mongo;

import com.sajo.operation_service.client.PrometheusClient;
import com.sajo.operation_service.client.PrometheusQueryResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

// MongoDB 자체(알람 컨텍스트 없이)의 기본 상태 - DependencyMappingService가 관련 대상 조회에 쓰고,
// 나중에 Mongo 관련 알람 전용 Strategy가 생기면 그 안에서도 재사용한다.
@Service
@RequiredArgsConstructor
public class MongoDiagnosticsService {

    private final PrometheusClient prometheusClient;

    public Map<String, PrometheusQueryResult> collect(Instant time) {
        Map<String, PrometheusQueryResult> metrics = new LinkedHashMap<>();

        metrics.put("MongoDB 연결 상태(up)", prometheusClient.query(MongoDiagnosticsQueries.up(), time));
        metrics.put("MongoDB 커넥션 사용률(0~1)", prometheusClient.query(MongoDiagnosticsQueries.connectionsUsage(), time));

        return metrics;
    }

    // MongoConnectionDown 전용 - collect()에 없는 트래픽을 더 본다.
    // DependencyMappingService는 collect()만 쓰므로 영향 없다.
    public Map<String, PrometheusQueryResult> collectForConnectionDown(Instant time) {
        Map<String, PrometheusQueryResult> metrics = new LinkedHashMap<>(collect(time));

        metrics.put("초당 op 처리량", prometheusClient.query(MongoDiagnosticsQueries.opRate(), time));

        return metrics;
    }
}
