package com.sajo.operation_service.service;

import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Optional;

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
        Optional<String> analysis;
        try {
            analysis = alertAnalyzer.analyze(alert);
        } catch (Exception e) {
            log.error("알람 분석 실패. alertname={}, application={}",
                    alert.labels().get("alertname"), alert.labels().get("application"), e);
            analysis = Optional.empty();
        }

        // 전략 미등록이든 LLM 호출 실패든, 분석이 없어도 알람 자체는 Slack에 전달한다 -
        // slack_configs 제거 후 "분석 안 되면 Slack에 아예 안 뜸"이 되는 회귀를 막기 위함.
        if (analysis.isPresent()) {
            log.info("알람 분석 결과. alertname={}, application={}\n{}",
                    alert.labels().get("alertname"),
                    alert.labels().get("application"),
                    analysis.get()
            );
            slackNotifier.notify(alert, analysis.get());
        } else {
            slackNotifier.notifyWithoutAnalysis(alert);
        }
    }

    private void notifyResolved(AlertManagerWebhookRequest.Alert alert) {
        try {
            slackNotifier.notifyResolved(alert);
        } catch (Exception e) {
            log.error("복구 알림 발송 실패. alertname={}, application={}",
                    alert.labels().get("alertname"), alert.labels().get("application"), e);
        }
    }
}
