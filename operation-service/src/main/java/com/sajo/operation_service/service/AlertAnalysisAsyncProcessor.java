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

    // 알람 하나 처리(분석/Slack 발송 전체) 중 어디서 예외가 나든 여기서 흡수한다 - forEach 순회
    // 중 예외가 새면 그 뒤 알람들이 조용히 누락되기 때문에, 개별 단계가 아니라 알람 단위로 감싼다.
    private void processOne(AlertManagerWebhookRequest.Alert alert) {
        try {
            if (alert.isFiring()) {
                analyzeOne(alert);
            } else {
                notifyResolved(alert);
            }
        } catch (Exception e) {
            log.error("알람 처리 실패. alertname={}, application={}",
                    alert.labels().get("alertname"), alert.labels().get("application"), e);
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
        slackNotifier.notifyResolved(alert);
    }
}
