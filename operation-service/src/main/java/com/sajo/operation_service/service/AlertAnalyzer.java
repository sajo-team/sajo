package com.sajo.operation_service.service;

import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest;
import com.sajo.operation_service.service.dto.AppDiagnosticsSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertAnalyzer {

    private static final String SYSTEM_PROMPT = """
                너는 SRE 어시스턴트다.
                제공된 알람과 메트릭만 사용하여 다음 형식으로 답해라.
                1. 관찰된 사실
                2. 원인 후보 최대 3개와 확신도(상/중/하)
                3. 다음 확인 항목
                메트릭만으로 확정할 수 없는 원인은 반드시 '후보'라고 표현하세요.
                수치만으로 확정할 수 없는 부분은 추측임을 명시해라.
            """;

    private final DiagnosticsService diagnosticsService;
    private final ChatClient chatClient;

    @Async("alertAnalysisExecutor")
    public void process(AlertManagerWebhookRequest request) {
        // 1. firing 인지 확인
        List<AlertManagerWebhookRequest.Alert> firing = request.alerts().stream()
                .filter(alert -> alert.status().equals("firing"))
                .toList();

        for (AlertManagerWebhookRequest.Alert alert : firing) {
            // 2. 서비스 필터
            String application = alert.labels().get("application");
            if (application == null) {
                log.warn("application 라벨이 없는 알람 - 진단 스킵. alertname={}", alert.labels().get("alertname"));
                continue;
            }

            // 3. prometheus 요청
            AppDiagnosticsSnapshot snapshot = diagnosticsService.collect(application, alert.startsAt());

            // 4. 프롬프트 구성
            String userPrompt = createUserPrompt(alert, snapshot);

            // 5. llm 호출
            try {
                String analysis = chatClient.prompt()
                        .system(SYSTEM_PROMPT)
                        .user(userPrompt)
                        .call()
                        .content();

                // 6. 결과 출력 (일단 로그로만 남기고, 추후 슬랙 알림 가도록 수정)
                log.info("알람 분석 결과. alertname={}, application={}\n{}",
                        alert.labels().get("alertname"), application, analysis);
            } catch (Exception e) {
                log.error("LLM 분석 실패. alertname={}, application={}", alert.labels().get("alertname"), application, e);
            }
        }
    }

    private String createUserPrompt(AlertManagerWebhookRequest.Alert alert, AppDiagnosticsSnapshot snapshot) {
        return """
                [알람]
                이름: %s
                심각도: %s
                요약: %s
                설명: %s
                발생 시각: %s

                [진단 지표 (발생 시점 기준 조회)]
                p99 지연시간: %.3f초
                5xx 에러율: %.2f%%
                CPU 사용률: %.2f%%
                Heap(Old Gen) 사용률: %.2f%%
                """.formatted(
                alert.labels().get("alertname"),
                alert.labels().get("severity"),
                alert.annotations().get("summary"),
                alert.annotations().get("description"),
                alert.startsAt(),
                snapshot.p99LatencySeconds(),
                snapshot.errorRate() * 100,
                snapshot.cpuUsage() * 100,
                snapshot.heapUsageRatio() * 100
        );
    }
}
