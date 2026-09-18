package com.sajo.operation_service.service;

import com.sajo.operation_service.client.PrometheusQueryResult;
import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest;
import com.sajo.operation_service.service.app.DiagnosticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertAnalyzerTest {

    private DiagnosticsService diagnosticsService;
    private ChatClient chatClient;
    private AlertAnalyzer alertAnalyzer;

    @BeforeEach
    void setup() {
        diagnosticsService = mock(DiagnosticsService.class);
        chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        alertAnalyzer = new AlertAnalyzer(diagnosticsService, chatClient);
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
    @DisplayName("application 라벨이 없으면 IllegalArgumentException을 던진다")
    void analyze_missingApplicationLabel_throws() {
        AlertManagerWebhookRequest.Alert alert = createAlert(Map.of("alertname", "ServiceDown"));

        assertThatThrownBy(() -> alertAnalyzer.analyze(alert))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ServiceDown");

        verifyNoInteractions(diagnosticsService, chatClient);
    }

    @Test
    @DisplayName("application 라벨에 PromQL 인젝션에 쓰일 수 있는 문자가 섞이면 IllegalArgumentException을 던진다")
    void analyze_applicationLabelWithInvalidCharacters_throws() {
        AlertManagerWebhookRequest.Alert alert = createAlert(Map.of(
                "alertname", "HighCpuUsage",
                "application", "trading-service\"} or process_cpu_usage{application=\"a"
        ));

        assertThatThrownBy(() -> alertAnalyzer.analyze(alert))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(diagnosticsService, chatClient);
    }

    @Test
    @DisplayName("정상 케이스: 진단 지표를 조회하고 LLM 응답을 그대로 반환한다")
    void analyze_success_returnsLlmResponse() {
        AlertManagerWebhookRequest.Alert alert = createAlert(Map.of(
                "alertname", "HighCpuUsage", "application", "trading-service", "severity", "warning"
        ));

        Map<String, PrometheusQueryResult> metrics = Map.of(
                "CPU 사용률(0~1)", PrometheusQueryResult.success(
                        "process_cpu_usage{application=\"trading-service\"}",
                        List.of(new PrometheusQueryResult.Series(Map.of("application", "trading-service"), "0.92"))
                )
        );

        when(diagnosticsService.collect("trading-service", alert.startsAt())).thenReturn(metrics);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("분석 결과 텍스트");

        String result = alertAnalyzer.analyze(alert);

        assertThat(result).isEqualTo("분석 결과 텍스트");
        verify(diagnosticsService).collect("trading-service", alert.startsAt());
    }

    @Test
    @DisplayName("진단 지표 조회가 실패하면 예외가 그대로 전파된다")
    void analyze_diagnosticsFailure_propagatesException() {
        AlertManagerWebhookRequest.Alert alert = createAlert(Map.of(
                "alertname", "HighCpuUsage", "application", "trading-service"
        ));

        when(diagnosticsService.collect(anyString(), any(Instant.class)))
                .thenThrow(new RuntimeException("Prometheus 타임아웃"));

        assertThatThrownBy(() -> alertAnalyzer.analyze(alert))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Prometheus 타임아웃");

        verifyNoInteractions(chatClient);
    }

    @Test
    @DisplayName("LLM 호출이 실패하면 예외가 그대로 전파된다")
    void analyze_llmFailure_propagatesException() {
        AlertManagerWebhookRequest.Alert alert = createAlert(Map.of(
                "alertname", "HighCpuUsage", "application", "trading-service"
        ));

        when(diagnosticsService.collect(anyString(), any(Instant.class))).thenReturn(Map.of());
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenThrow(new RuntimeException("OpenAI API error"));

        assertThatThrownBy(() -> alertAnalyzer.analyze(alert))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("OpenAI API error");
    }
}
