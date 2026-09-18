package com.sajo.operation_service.service.dependency;

import com.sajo.operation_service.client.PrometheusClient;
import com.sajo.operation_service.client.PrometheusQueryResult;
import com.sajo.operation_service.service.app.DiagnosticsService;
import com.sajo.operation_service.service.kafka.KafkaDiagnosticsService;
import com.sajo.operation_service.service.mongo.MongoDiagnosticsService;
import com.sajo.operation_service.service.postgres.PostgresDiagnosticsService;
import com.sajo.operation_service.service.redis.RedisDiagnosticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 서비스<->인프라 의존관계는 Prometheus/LLM이 자동으로 알 수 없어서 개발자가 직접 정의한 정적 매핑이다.
// 방향 구분 없이 같은 표를 양방향으로 쓴다 - 인프라 알람이면 "누가 이 인프라에 의존하는지",
// 서비스 알람이면 "이 서비스가 무엇에 의존하는지"를 같은 맵에서 찾는다.
// 관련 대상 각각의 실제 조회는 그 대상 타입 전용 진단 클래스(RedisDiagnosticsService 등)에 위임한다 -
// 단순 up/down이 아니라 "살아있지만 저하된 상태"(커넥션 고갈, 메모리 임계치 등)까지 봐야
// 실제 원인 후보(예: HikariPoolPending의 원인이 Postgres 커넥션 사용률 95%)를 잡을 수 있기 때문.
// 같은 진단 클래스를 나중에 그 대상 자신의 알람 전용 Strategy에서도 재사용한다.
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
    private final DiagnosticsService diagnosticsService;
    private final RedisDiagnosticsService redisDiagnosticsService;
    private final PostgresDiagnosticsService postgresDiagnosticsService;
    private final MongoDiagnosticsService mongoDiagnosticsService;
    private final KafkaDiagnosticsService kafkaDiagnosticsService;

    public Map<String, PrometheusQueryResult> collect(String target, Instant time) {
        List<String> relatedTargets = DEPENDENCY_MAP.getOrDefault(target, List.of());

        Map<String, PrometheusQueryResult> metrics = new LinkedHashMap<>();
        for (String relatedTarget : relatedTargets) {
            collectFor(relatedTarget, time)
                    .forEach((label, result) -> metrics.put("[의존 대상: " + relatedTarget + "] " + label, result));
        }

        return metrics;
    }

    private Map<String, PrometheusQueryResult> collectFor(String relatedTarget, Instant time) {
        return switch (relatedTarget) {
            case "redis" -> redisDiagnosticsService.collect(time);
            case "postgres" -> postgresDiagnosticsService.collect(time);
            case "mongo" -> mongoDiagnosticsService.collect(time);
            case "user-service", "market-service", "trading-service" ->
                    diagnosticsService.collect(relatedTarget, time);
            // 브로커 연결 상태(kafka_brokers)만 본다 - 컨슈머그룹/토픽 단위 lag은 그룹/토픽을 알아야
            // 의미가 생기는데 DEPENDENCY_MAP은 대상 이름만 알아서 여기선 못 다룸(나중에 알람 자신의
            // 라벨을 직접 읽는 KafkaConsumerGroupStrategy에서 처리).
            case "kafka" -> kafkaDiagnosticsService.collect(time);
            // DEPENDENCY_MAP에 위 케이스로 안 잡히는 대상이 추가될 경우를 위한 방어적 fallback
            default -> Map.of(
                    "up", prometheusClient.query(DependencyMappingQueries.upStatus(relatedTarget), time)
            );
        };
    }
}
