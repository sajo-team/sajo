package com.sajo.trading_service.ai_risk.outbox;

import com.sajo.trading_service.ai_risk.kafka.outbox.AiRiskAnalysisOutboxPublisher;
import com.sajo.trading_service.ai_risk.kafka.outbox.AiRiskAnalysisOutboxScheduler;
import com.sajo.trading_service.outbox.service.OutboxEventStatusService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.inOrder;

@Tag("ai-risk")
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class AiRiskAnalysisOutboxSchedulerTest {

    @Mock
    private AiRiskAnalysisOutboxPublisher outboxPublisher;

    @Mock
    private OutboxEventStatusService outboxEventStatusService;

    @Test
    void 고착된_PROCESSING_이벤트를_복구한_후_PENDING_이벤트를_발행한다() {
        AiRiskAnalysisOutboxScheduler scheduler =
                new AiRiskAnalysisOutboxScheduler(
                        outboxPublisher,
                        outboxEventStatusService
                );

        scheduler.publishPendingEvents();

        InOrder inOrder = inOrder(
                outboxEventStatusService,
                outboxPublisher
        );

        inOrder.verify(outboxEventStatusService)
                .recoverStaleProcessingEvents();

        inOrder.verify(outboxPublisher)
                .publishPendingEvents();
    }
}