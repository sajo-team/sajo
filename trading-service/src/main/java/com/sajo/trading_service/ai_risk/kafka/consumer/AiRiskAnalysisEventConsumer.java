package com.sajo.trading_service.ai_risk.kafka.consumer;

import com.sajo.trading_service.ai_risk.kafka.dto.AiRiskAnalysisRequestedEvent;
import com.sajo.trading_service.ai_risk.service.processor.AiRiskAnalysisProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiRiskAnalysisEventConsumer {

    private static final String TOPIC = "airiskanalysis.requested";

    private final AiRiskAnalysisProcessor processor;

    @KafkaListener(
            topics = TOPIC,
            containerFactory = "aiRiskListenerFactory"
    )
    public void consume(AiRiskAnalysisRequestedEvent event) {

        if (!AiRiskAnalysisRequestedEvent.EVENT_TYPE.equals(event.eventType())) {
            log.warn(
                    "지원하지 않는 AI 위험 분석 이벤트 타입. eventId={}, eventType={}",
                    event.eventId(),
                    event.eventType()
            );
            return;
        }

        if (event.eventVersion() != AiRiskAnalysisRequestedEvent.EVENT_VERSION) {
            log.warn(
                    "지원하지 않는 AI 위험 분석 이벤트 버전. eventId={}, eventVersion={}",
                    event.eventId(),
                    event.eventVersion()
            );
            return;
        }

        log.info(
                "AI 위험 분석 이벤트 수신. eventId={}, analysisId={}",
                event.eventId(),
                event.payload().analysisId()
        );

        processor.process(event);
    }
}
