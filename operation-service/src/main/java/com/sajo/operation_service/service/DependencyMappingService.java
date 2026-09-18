package com.sajo.operation_service.service;

import com.sajo.operation_service.client.PrometheusClient;
import com.sajo.operation_service.client.PrometheusQueryResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 서비스<->인프라 의존관계는 Prometheus/LLM이 자동으로 알 수 없어서 개발자가 직접 정의한 정적 매핑이다.
// 방향 구분 없이 같은 표를 양방향으로 쓴다 - 인프라 알람이면 "누가 이 인프라에 의존하는지",
// 서비스 알람이면 "이 서비스가 무엇에 의존하는지"를 같은 맵에서 찾는다.
// 대상마다 동작(조회 방식) 자체는 항상 동일하고 데이터(관련 대상 목록)만 다르므로 전략 패턴은 쓰지 않는다.
@Service
@RequiredArgsConstructor
public class DependencyMappingService {

    private static final Map<String, List<String>> DEPENDENCY_MAP = Map.of(
            "postgres", List.of("user-service", "market-service", "trading-service"),
            "redis", List.of("user-service", "market-service"),
            "mongo", List.of("trading-service"),
            "kafka", List.of("market-service", "trading-service"),
            "user-service", List.of("postgres", "redis"),
            "market-service", List.of("postgres", "redis", "kafka"),
            "trading-service", List.of("postgres", "mongo", "kafka")
    );

    private final PrometheusClient prometheusClient;

    public Map<String, PrometheusQueryResult> collect(String target, Instant time) {
        List<String> relatedTargets = DEPENDENCY_MAP.getOrDefault(target, List.of());

        Map<String, PrometheusQueryResult> metrics = new LinkedHashMap<>();
        for (String relatedTarget : relatedTargets) {
            metrics.put(
                    "의존 대상 상태(up): " + relatedTarget,
                    prometheusClient.query(DependencyMappingQueries.upStatus(relatedTarget), time)
            );
        }

        return metrics;
    }
}
