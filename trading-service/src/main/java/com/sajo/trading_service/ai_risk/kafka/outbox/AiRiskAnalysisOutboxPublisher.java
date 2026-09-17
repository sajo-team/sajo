package com.sajo.trading_service.ai_risk.kafka.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajo.trading_service.ai_risk.kafka.dto.AiRiskAnalysisRequestedEvent;
import com.sajo.trading_service.ai_risk.kafka.producer.AiRiskAnalysisEventProducer;
import com.sajo.trading_service.outbox.domain.OutboxEvent;
import com.sajo.trading_service.outbox.domain.OutboxStatus;
import com.sajo.trading_service.outbox.repository.OutboxEventRepository;
import com.sajo.trading_service.outbox.service.OutboxEventStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiRiskAnalysisOutboxPublisher {

    private static final int BATCH_SIZE = 100;

    private final OutboxEventRepository outboxEventRepository;
    private final AiRiskAnalysisEventProducer eventProducer;
    private final ObjectMapper objectMapper;
    private final OutboxEventStatusService outboxEventStatusService;

    public void publishPendingEvents(){
        List<OutboxEvent> events = outboxEventRepository.findByStatusAndEventTypeOrderByCreatedAtAsc(
                OutboxStatus.PENDING,
                AiRiskAnalysisRequestedEvent.EVENT_TYPE,
                PageRequest.of(0, BATCH_SIZE)
        );

        for (OutboxEvent outboxEvent : events) {
            try {
                if (!outboxEventStatusService.claimForPublish(outboxEvent.getId())) {
                    log.debug(
                            "다른 인스턴스가 이미 Outbox 이벤트를 선점했습니다. eventId={}",
                            outboxEvent.getId()
                    );
                    continue;
                }
            } catch (Exception exception) {
                log.error(
                        "Outbox 이벤트 선점 중 오류가 발생했습니다. eventId={}",
                        outboxEvent.getId(),
                        exception
                );
                continue;
            }

            publish(outboxEvent);
        }
    }

    private void handlePublishFailure(UUID eventId, Exception exception) {
        OutboxEvent event =
                outboxEventStatusService.handlePublishFailure(eventId);

        if (event.getStatus() == OutboxStatus.FAILED) {
            log.error(
                    "AI 위험 분석 Outbox 이벤트 최종 발행 실패. eventId={}, retryCount={}",
                    eventId,
                    event.getRetryCount(),
                    exception
            );
            return;
        }

        log.warn(
                "AI 위험 분석 Outbox 이벤트 발행 실패. eventId={}, retryCount={}",
                eventId,
                event.getRetryCount(),
                exception
        );
    }

    private void publish(OutboxEvent outboxEvent) {
        AiRiskAnalysisRequestedEvent event;

        try {
            event = objectMapper.treeToValue(
                    outboxEvent.getEventBody(),
                    AiRiskAnalysisRequestedEvent.class
            );
        } catch (Exception exception) {
            handlePublishFailure(outboxEvent.getId(), exception);
            return;
        }

        try {
            eventProducer.publish(event);
        } catch (Exception exception) {
            handlePublishFailure(outboxEvent.getId(), exception);
            return;
        }

        try {
            outboxEventStatusService.markPublished(outboxEvent.getId());

            log.info(
                    "AI 위험 분석 Outbox 이벤트 발행 완료. eventId={}",
                    outboxEvent.getId()
            );
        } catch (Exception exception) {
            log.error(
                    "Kafka 발행 성공 후 Outbox 상태 갱신 실패. eventId={}",
                    outboxEvent.getId(),
                    exception
            );
        }
    }
}
