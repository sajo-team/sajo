package com.sajo.trading_service.ai_risk.kafka.producer;

import com.sajo.trading_service.ai_risk.kafka.dto.AiRiskAnalysisRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@RequiredArgsConstructor
public class AiRiskAnalysisEventProducer {

    private static final String TOPIC = "airiskanalysis.requested";
    private static final long PUBLISH_TIMEOUT_SECONDS = 10L;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(AiRiskAnalysisRequestedEvent event){
        try{
            kafkaTemplate.send(
                    TOPIC,
                    event.payload().analysisId().toString(), //key
                    event // value
            ).get(PUBLISH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception){
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "AI 위험 분석 Kafka 이벤트 발행이 중단되었습니다.",
                    exception
            );
        } catch (ExecutionException exception){
            throw new IllegalStateException(
                    "AI 위험 분석 Kafka 이벤트 발행에 실패했습니다.",
                    exception.getCause()
            );
        } catch (TimeoutException exception){
            throw new IllegalStateException(
                    "AI 위험 분석 Kafka 이벤트 발행 시간이 초과되었습니다.",
                    exception
            );
        }
    }
}
