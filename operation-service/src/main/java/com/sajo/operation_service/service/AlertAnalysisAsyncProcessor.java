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
    private final SlackNotifier slackNotifier;

    @Async("alertAnalysisExecutor")
    public void process(AlertManagerWebhookRequest request) {
        request.alerts().forEach(this::processOne);
    }

    private void processOne(AlertManagerWebhookRequest.Alert alert) {
        if (alert.isFiring()) {
            analyzeOne(alert);
        } else {
            notifyResolved(alert);
        }
    }

    private void analyzeOne(AlertManagerWebhookRequest.Alert alert) {
        try {
            alertAnalyzer.analyze(alert).ifPresent(analysis -> {
                log.info("알람 분석 결과. alertname={}, application={}\n{}",
                        alert.labels().get("alertname"),
                        alert.labels().get("application"),
                        analysis
                );

                slackNotifier.notify(alert, analysis);
            });
        } catch (Exception e) {
            log.error("알람 분석 실패. alertname={}, application={}",
                    alert.labels().get("alertname"), alert.labels().get("application"), e);
        }
    }

    // resolved 알람은 LLM 분석을 다시 돌리지 않는다
    // 여기서 필요한 건 "복구됐다"는 사실 전달뿐이다.
    private void notifyResolved(AlertManagerWebhookRequest.Alert alert) {
        try {
            slackNotifier.notifyResolved(alert);
        } catch (Exception e) {
            log.error("복구 알림 발송 실패. alertname={}, application={}",
                    alert.labels().get("alertname"), alert.labels().get("application"), e);
        }
    }
}
