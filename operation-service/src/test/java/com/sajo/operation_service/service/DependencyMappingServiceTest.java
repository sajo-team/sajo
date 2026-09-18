package com.sajo.operation_service.service;

import com.sajo.operation_service.client.PrometheusClient;
import com.sajo.operation_service.client.PrometheusQueryResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DependencyMappingServiceTest {

    private PrometheusClient prometheusClient;
    private DependencyMappingService dependencyMappingService;

    @BeforeEach
    void setup() {
        prometheusClient = mock(PrometheusClient.class);
        dependencyMappingService = new DependencyMappingService(prometheusClient);
    }

    @Test
    @DisplayName("인프라 대상(redis)이면 그 인프라에 의존하는 서비스들의 up 상태를 조회한다")
    void collect_infraTarget_queriesDependentServices() {
        Instant time = Instant.parse("2026-09-18T03:00:00Z");
        when(prometheusClient.query(anyString(), eq(time)))
                .thenReturn(PrometheusQueryResult.success("query", List.of()));

        Map<String, PrometheusQueryResult> metrics = dependencyMappingService.collect("redis", time);

        assertThat(metrics).hasSize(2);
        verify(prometheusClient).query(eq("up{application=\"user-service\"}"), eq(time));
        verify(prometheusClient).query(eq("up{application=\"market-service\"}"), eq(time));
    }

    @Test
    @DisplayName("서비스 대상(trading-service)이면 그 서비스가 의존하는 인프라들의 up 상태를 조회한다")
    void collect_serviceTarget_queriesDependencyInfra() {
        Instant time = Instant.parse("2026-09-18T03:00:00Z");
        when(prometheusClient.query(anyString(), eq(time)))
                .thenReturn(PrometheusQueryResult.success("query", List.of()));

        Map<String, PrometheusQueryResult> metrics = dependencyMappingService.collect("trading-service", time);

        assertThat(metrics).hasSize(3);
        verify(prometheusClient).query(eq("up{application=\"postgres\"}"), eq(time));
        verify(prometheusClient).query(eq("up{application=\"mongo\"}"), eq(time));
        verify(prometheusClient).query(eq("up{application=\"kafka\"}"), eq(time));
    }

    @Test
    @DisplayName("매핑에 없는 대상이면 조회 없이 빈 맵을 반환한다")
    void collect_unmappedTarget_returnsEmptyWithoutQuerying() {
        Instant time = Instant.parse("2026-09-18T03:00:00Z");

        Map<String, PrometheusQueryResult> metrics = dependencyMappingService.collect("node", time);

        assertThat(metrics).isEmpty();
        verify(prometheusClient, never()).query(anyString(), eq(time));
    }
}
