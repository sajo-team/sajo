package com.sajo.trading_service.ai_risk.kafka.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiRiskAnalysisOutboxScheduler {

    private final AiRiskAnalysisOutboxPublisher outboxPublisher;

    @Scheduled(fixedDelayString = "${outbox.ai-risk.publish-delay-ms:1000}")
    public void publishPendingEvents(){
        outboxPublisher.publishPendingEvents();
    }
}
