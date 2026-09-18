package com.sajo.operation_service.service.dependency;

import com.sajo.operation_service.client.PrometheusClient;
import com.sajo.operation_service.client.PrometheusQueryResult;
import com.sajo.operation_service.service.app.DiagnosticsService;
import com.sajo.operation_service.service.kafka.KafkaDiagnosticsService;
import com.sajo.operation_service.service.mongo.MongoDiagnosticsService;
import com.sajo.operation_service.service.postgres.PostgresDiagnosticsService;
import com.sajo.operation_service.service.redis.RedisDiagnosticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DependencyMappingServiceTest {

    private PrometheusClient prometheusClient;
    private DiagnosticsService diagnosticsService;
    private RedisDiagnosticsService redisDiagnosticsService;
    private PostgresDiagnosticsService postgresDiagnosticsService;
    private MongoDiagnosticsService mongoDiagnosticsService;
    private KafkaDiagnosticsService kafkaDiagnosticsService;
    private DependencyMappingService dependencyMappingService;

    @BeforeEach
    void setup() {
        prometheusClient = mock(PrometheusClient.class);
        diagnosticsService = mock(DiagnosticsService.class);
        redisDiagnosticsService = mock(RedisDiagnosticsService.class);
        postgresDiagnosticsService = mock(PostgresDiagnosticsService.class);
        mongoDiagnosticsService = mock(MongoDiagnosticsService.class);
        kafkaDiagnosticsService = mock(KafkaDiagnosticsService.class);
        dependencyMappingService = new DependencyMappingService(
                prometheusClient, diagnosticsService, redisDiagnosticsService, postgresDiagnosticsService,
                mongoDiagnosticsService, kafkaDiagnosticsService
        );
    }

    @Test
    @DisplayName("인프라 대상(redis)이면 그 인프라에 의존하는 서비스들을 DiagnosticsService로 조회한다")
    void collect_infraTarget_queriesDependentServicesViaDiagnosticsService() {
        Instant time = Instant.parse("2026-09-18T03:00:00Z");
        PrometheusQueryResult dummy = PrometheusQueryResult.success("query", List.of());
        when(diagnosticsService.collect("user-service", time)).thenReturn(Map.of("CPU 사용률(0~1)", dummy));
        when(diagnosticsService.collect("market-service", time)).thenReturn(Map.of("CPU 사용률(0~1)", dummy));

        Map<String, PrometheusQueryResult> metrics = dependencyMappingService.collect("redis", time);

        assertThat(metrics).hasSize(2);
        assertThat(metrics).containsKeys(
                "[의존 대상: user-service] CPU 사용률(0~1)",
                "[의존 대상: market-service] CPU 사용률(0~1)"
        );
        verifyNoInteractions(redisDiagnosticsService, postgresDiagnosticsService, mongoDiagnosticsService, prometheusClient);
    }

    @Test
    @DisplayName("서비스 대상(trading-service)이면 postgres/mongo/kafka 각각 전용 진단 클래스로 조회한다")
    void collect_serviceTarget_queriesDependencyInfraViaDedicatedServices() {
        Instant time = Instant.parse("2026-09-18T03:00:00Z");
        PrometheusQueryResult dummy = PrometheusQueryResult.success("query", List.of());
        when(postgresDiagnosticsService.collect(time)).thenReturn(Map.of("Postgres 커넥션 사용률(0~1)", dummy));
        when(mongoDiagnosticsService.collect(time)).thenReturn(Map.of("MongoDB 커넥션 사용률(0~1)", dummy));
        when(kafkaDiagnosticsService.collect(time)).thenReturn(Map.of("Kafka 브로커 수", dummy));

        Map<String, PrometheusQueryResult> metrics = dependencyMappingService.collect("trading-service", time);

        assertThat(metrics).hasSize(3);
        assertThat(metrics).containsKeys(
                "[의존 대상: postgres] Postgres 커넥션 사용률(0~1)",
                "[의존 대상: mongo] MongoDB 커넥션 사용률(0~1)",
                "[의존 대상: kafka] Kafka 브로커 수"
        );
        verifyNoInteractions(diagnosticsService, redisDiagnosticsService, prometheusClient);
    }

    @Test
    @DisplayName("매핑에 없는 대상이면 조회 없이 빈 맵을 반환한다")
    void collect_unmappedTarget_returnsEmptyWithoutQuerying() {
        Instant time = Instant.parse("2026-09-18T03:00:00Z");

        Map<String, PrometheusQueryResult> metrics = dependencyMappingService.collect("node", time);

        assertThat(metrics).isEmpty();
        verifyNoInteractions(prometheusClient, diagnosticsService, redisDiagnosticsService, postgresDiagnosticsService,
                mongoDiagnosticsService, kafkaDiagnosticsService);
    }
}
