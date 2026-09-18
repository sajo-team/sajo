package com.sajo.operation_service.service;

import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertAnalysisAsyncProcessor {

    private final AlertAnalyzer alertAnalyzer;

    @Async("alertAnalysisExecutor")
    public void process(AlertManagerWebhookRequest request) {
        request.alerts().stream()
                .filter(AlertManagerWebhookRequest.Alert::isFiring)
                .forEach(this::analyzeOne);
    }
    private void analyzeOne(AlertManagerWebhookRequest.Alert alert) {
        try {
            // 전략이 아직 없는 alertname은 AlertAnalyzer가 빈 Optional을 반환한다(자체적으로 로그를 남김) -
            // 여기서는 그 경우 조용히 넘어간다.
            alertAnalyzer.analyze(alert).ifPresent(analysis ->
                    //TODO: 추후 슬랙 알림으로 수정
                    log.info("알람 분석 결과. alertname={}, application={}\n{}",
                            alert.labels().get("alertname"), alert.labels().get("application"), analysis)
            );
        } catch (Exception e) {
            log.error("알람 분석 실패. alertname={}, application={}",
                    alert.labels().get("alertname"), alert.labels().get("application"), e);
        }
    }
}
