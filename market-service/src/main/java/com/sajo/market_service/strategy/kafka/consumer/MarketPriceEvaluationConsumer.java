package com.sajo.market_service.strategy.kafka.consumer;

import com.sajo.market_service.market.kafka.dto.MarketPricePayload;
import com.sajo.market_service.market.kafka.dto.MarketPriceUpdatedEvent;
import com.sajo.market_service.strategy.controller.dto.request.StrategyEvaluationRequest;
import com.sajo.market_service.strategy.service.command.StrategyEvaluationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * {@code market.price.updated} 이벤트를 받아 전략 평가로 연결한다. 서비스 기본 컨슈머 그룹
 * (market-consumer-group)과 오프셋을 분리하기 위해 groupId를 전용으로 오버라이드한다.
 * {@link StrategyEvaluationService#evaluate}가 이미 전략 단위로 예외를 흡수하므로
 * 여기서는 인프라성 예외만 이 컨슈머 전용 에러 핸들러
 * ({@link com.sajo.market_service.strategy.kafka.config.StrategyKafkaConfig}, 재시도 후
 * {@code market.price.updated.DLT}로 전송)에 맡긴다. market 패키지의 다른 컨슈머는 서비스
 * 기본 정책(재시도 후 스킵)을 그대로 쓰므로 영향받지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketPriceEvaluationConsumer {

    private final StrategyEvaluationService strategyEvaluationService;

    @KafkaListener(
            topics = "market.price.updated",
            groupId = "strategy-evaluation-group",
            concurrency = "3",
            containerFactory = "strategyEvaluationListenerFactory"
    )
    public void consume(MarketPriceUpdatedEvent event) {
        MarketPricePayload payload = event.payload();

        log.debug("시세 이벤트로 전략 평가를 시작합니다. eventId={}, stockCode={}, currentPrice={}",
                event.eventId(), payload.stockCode(), payload.currentPrice());

        strategyEvaluationService.evaluate(new StrategyEvaluationRequest(
                event.eventId(),
                payload.stockCode(),
                payload.currentPrice(),
                payload.tradedAt()
        ));
    }
}
