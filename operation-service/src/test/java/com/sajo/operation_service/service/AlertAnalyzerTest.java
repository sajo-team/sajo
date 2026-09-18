package com.sajo.operation_service.service;

import com.sajo.operation_service.client.PrometheusQueryResult;
import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest;
import com.sajo.operation_service.service.dependency.DependencyMappingService;
import com.sajo.operation_service.service.host.HostDiagnosticsService;
import com.sajo.operation_service.service.strategy.AppMetricsStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertAnalyzerTest {

    private HostDiagnosticsService hostDiagnosticsService;
    private DependencyMappingService dependencyMappingService;
    private ChatClient chatClient;
    private AppMetricsStrategy appMetricsStrategy;
    private AlertAnalyzer alertAnalyzer;

    @BeforeEach
    void setup() {
        hostDiagnosticsService = mock(HostDiagnosticsService.class);
        dependencyMappingService = mock(DependencyMappingService.class);
        chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        appMetricsStrategy = mock(AppMetricsStrategy.class);
        alertAnalyzer = new AlertAnalyzer(hostDiagnosticsService, dependencyMappingService, chatClient, appMetricsStrategy);
    }

    private AlertManagerWebhookRequest.Alert createAlert(Map<String, String> labels) {
        return new AlertManagerWebhookRequest.Alert(
                "firing",
                labels,
                Map.of("summary", "요약", "description", "설명"),
                Instant.parse("2026-09-17T03:00:00Z")
        );
    }

    @Test
    @DisplayName("정상 케이스: 호스트+의존관계+전략 지표를 모두 조회하고 LLM 응답을 반환한다")
    void analyze_success_returnsLlmResponse() {
        AlertManagerWebhookRequest.Alert alert = createAlert(Map.of(
                "alertname", "HighCpuUsage", "application", "trading-service", "severity", "warning"
        ));
        PrometheusQueryResult dummy = PrometheusQueryResult.success(
                "query", List.of(new PrometheusQueryResult.Series(Map.of(), "0.92")));

        when(hostDiagnosticsService.collect(alert.startsAt())).thenReturn(Map.of("호스트 CPU 사용률(0~1)", dummy));
        when(dependencyMappingService.collect("trading-service", alert.startsAt()))
                .thenReturn(Map.of("[의존 대상: postgres] Postgres 커넥션 사용률(0~1)", dummy));
        when(appMetricsStrategy.diagnose(alert, alert.startsAt())).thenReturn(Map.of("CPU 사용률(0~1)", dummy));
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("분석 결과 텍스트");

        Optional<String> result = alertAnalyzer.analyze(alert);

        assertThat(result).contains("분석 결과 텍스트");
        verify(hostDiagnosticsService).collect(alert.startsAt());
        verify(dependencyMappingService).collect("trading-service", alert.startsAt());
        verify(appMetricsStrategy).diagnose(alert, alert.startsAt());
    }

    @Test
    @DisplayName("sajo-node 그룹(application=node)은 전략이 없어도 호스트+의존관계만으로 분석한다 - 호스트 스냅샷이 own snapshot을 대신함")
    void analyze_nodeGroupWithoutStrategy_stillAnalyzesUsingHostSnapshot() {
        AlertManagerWebhookRequest.Alert alert = createAlert(Map.of(
                "alertname", "HighNodeCpuUsage", "application", "node"
        ));
        PrometheusQueryResult dummy = PrometheusQueryResult.success("query", List.of());

        when(hostDiagnosticsService.collect(alert.startsAt())).thenReturn(Map.of("호스트 CPU 사용률(0~1)", dummy));
        when(dependencyMappingService.collect("node", alert.startsAt())).thenReturn(Map.of());
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("분석 결과 텍스트");

        Optional<String> result = alertAnalyzer.analyze(alert);

        assertThat(result).contains("분석 결과 텍스트");
        verifyNoInteractions(appMetricsStrategy);
    }

    @Test
    @DisplayName("node가 아닌데 전략도 없는 alertname이면 분석 자체를 건너뛴다(호스트/의존관계/LLM 전부 호출 안 함) - 근거 없는 분석문을 만들지 않기 위함")
    void analyze_unmappedNonNodeAlertname_skipsAnalysisEntirely() {
        AlertManagerWebhookRequest.Alert alert = createAlert(Map.of(
                "alertname", "RedisMemoryHigh", "application", "redis"
        ));

        Optional<String> result = alertAnalyzer.analyze(alert);

        assertThat(result).isEmpty();
        verifyNoInteractions(hostDiagnosticsService, dependencyMappingService, appMetricsStrategy, chatClient);
    }

    @Test
    @DisplayName("전략이 없고 application 라벨 자체도 없으면(node 여부를 알 수 없음) 분석을 건너뛴다")
    void analyze_unmappedAlertnameWithoutApplicationLabel_skipsAnalysisEntirely() {
        AlertManagerWebhookRequest.Alert alert = createAlert(Map.of("alertname", "ServiceDown"));

        Optional<String> result = alertAnalyzer.analyze(alert);

        assertThat(result).isEmpty();
        verifyNoInteractions(hostDiagnosticsService, dependencyMappingService, appMetricsStrategy, chatClient);
    }

    @Test
    @DisplayName("application 라벨이 없어도 전략이 있는 alertname이면 의존관계 조회만 건너뛰고 분석은 계속한다")
    void analyze_strategyMappedButMissingApplicationLabel_skipsDependencyLookupOnly() {
        AlertManagerWebhookRequest.Alert alert = createAlert(Map.of("alertname", "HighCpuUsage"));
        PrometheusQueryResult dummy = PrometheusQueryResult.success("query", List.of());

        when(hostDiagnosticsService.collect(alert.startsAt())).thenReturn(Map.of("호스트 CPU 사용률(0~1)", dummy));
        when(appMetricsStrategy.diagnose(alert, alert.startsAt()))
                .thenThrow(new IllegalArgumentException("application 라벨이 없는 알람"));

        assertThatThrownBy(() -> alertAnalyzer.analyze(alert))
                .isInstanceOf(IllegalArgumentException.class);

        verify(dependencyMappingService, never()).collect(anyString(), any(Instant.class));
    }

    @Test
    @DisplayName("전략에서 검증 실패(예: application 라벨 문제)로 예외가 나면 그대로 전파되고 LLM은 호출되지 않는다")
    void analyze_strategyFailure_propagatesExceptionWithoutCallingLlm() {
        AlertManagerWebhookRequest.Alert alert = createAlert(Map.of(
                "alertname", "HighCpuUsage",
                "application", "trading-service\"} or process_cpu_usage{application=\"a"
        ));

        when(hostDiagnosticsService.collect(alert.startsAt())).thenReturn(Map.of());
        when(dependencyMappingService.collect(eq(alert.labels().get("application")), eq(alert.startsAt())))
                .thenReturn(Map.of());
        when(appMetricsStrategy.diagnose(eq(alert), eq(alert.startsAt())))
                .thenThrow(new IllegalArgumentException("application 라벨 형식이 올바르지 않은 알람"));

        assertThatThrownBy(() -> alertAnalyzer.analyze(alert))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(chatClient);
    }

    @Test
    @DisplayName("호스트 진단 조회가 실패하면 예외가 그대로 전파된다")
    void analyze_hostDiagnosticsFailure_propagatesException() {
        AlertManagerWebhookRequest.Alert alert = createAlert(Map.of(
                "alertname", "HighCpuUsage", "application", "trading-service"
        ));

        when(hostDiagnosticsService.collect(any(Instant.class)))
                .thenThrow(new RuntimeException("Prometheus 타임아웃"));

        assertThatThrownBy(() -> alertAnalyzer.analyze(alert))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Prometheus 타임아웃");

        verifyNoInteractions(dependencyMappingService, appMetricsStrategy, chatClient);
    }

    @Test
    @DisplayName("LLM 호출이 실패하면 예외가 그대로 전파된다")
    void analyze_llmFailure_propagatesException() {
        AlertManagerWebhookRequest.Alert alert = createAlert(Map.of(
                "alertname", "HighCpuUsage", "application", "trading-service"
        ));

        when(hostDiagnosticsService.collect(any(Instant.class))).thenReturn(Map.of());
        when(dependencyMappingService.collect(anyString(), any(Instant.class))).thenReturn(Map.of());
        when(appMetricsStrategy.diagnose(any(), any())).thenReturn(Map.of());
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenThrow(new RuntimeException("OpenAI API error"));

        assertThatThrownBy(() -> alertAnalyzer.analyze(alert))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("OpenAI API error");
    }
}
