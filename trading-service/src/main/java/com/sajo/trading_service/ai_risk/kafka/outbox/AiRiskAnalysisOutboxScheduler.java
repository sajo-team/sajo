package com.sajo.trading_service.ai_risk.kafka.outbox;

import com.sajo.trading_service.outbox.service.OutboxEventStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiRiskAnalysisOutboxScheduler {

    private final AiRiskAnalysisOutboxPublisher outboxPublisher;
    private final OutboxEventStatusService outboxEventStatusService;

    @Scheduled(fixedDelayString = "${outbox.ai-risk.publish-delay-ms:1000}")
    public void publishPendingEvents(){
        int recoveredCount = outboxEventStatusService.recoverStaleProcessingEvents();

        if(recoveredCount > 0){
            log.warn(
                    "고착된 Outbox PROCESSING 이벤트를 복구했습니다. count={}",
                    recoveredCount
            );
        }
        outboxPublisher.publishPendingEvents();
    }
}
