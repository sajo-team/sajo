package com.sajo.trading_service.ai_risk.kafka.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajo.trading_service.ai_risk.kafka.dto.AiRiskAnalysisRequestedEvent;
import com.sajo.trading_service.ai_risk.kafka.producer.AiRiskAnalysisEventProducer;
import com.sajo.trading_service.outbox.domain.OutboxEvent;
import com.sajo.trading_service.outbox.domain.OutboxStatus;
import com.sajo.trading_service.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiRiskAnalysisOutboxPublisher {

    private static final int BATCH_SIZE = 100;

    private final OutboxEventRepository outboxEventRepository;
    private final AiRiskAnalysisEventProducer eventProducer;
    private final ObjectMapper objectMapper;

    @Transactional
    public void publishPendingEvents(){
        List<OutboxEvent> events = outboxEventRepository.findByStatusAndEventTypeOrderByCreatedAtAsc(
                OutboxStatus.PENDING,
                AiRiskAnalysisRequestedEvent.EVENT_TYPE,
                PageRequest.of(0, BATCH_SIZE)
        );

        for(OutboxEvent outboxEvent : events){
            publish(outboxEvent);
        }
    }

    private void publish(OutboxEvent outboxEvent){
        try{
            AiRiskAnalysisRequestedEvent event = objectMapper.treeToValue(
                    outboxEvent.getEventBody(),
                    AiRiskAnalysisRequestedEvent.class
            );

            eventProducer.publish(event);

            outboxEvent.markPublished();

            log.info(
                    "AI 위험 분석 Outbox 이벤트 발행 완료. eventId={}",
                    outboxEvent.getId()
            );
        } catch (Exception exception){
            outboxEvent.increaseRetryCount();

            log.error(
                    "AI 위험 분석 Outbox 이벤트 발행 실패, eventId={}, retryCount={}",
                    outboxEvent.getId(),
                    outboxEvent.getRetryCount(),
                    exception
            );
        }
    }
}
