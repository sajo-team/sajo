package com.sajo.market_service.market.kafka.consumer;

import com.sajo.market_service.market.kafka.dto.MarketQuoteRequestedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * {@code market.quote.requested} 이벤트를 받아 로그로 남긴다(#248).
 *
 * <p>이번 범위에서는 DB 저장 없이 로그 기록만 담당한다 — 추후 로그 수집 스택(Loki 등) 연동을
 * 검토하기 위한 발판이다. 역직렬화 실패·처리 중 예외는 공통 Kafka 에러 핸들러
 * ({@code CommonKafkaAutoConfiguration#defaultKafkaErrorHandler})가 재시도 후 스킵한다.</p>
 */
@Slf4j
@Component
public class MarketQuoteRequestConsumer {

    @KafkaListener(topics = "market.quote.requested")
    public void consume(MarketQuoteRequestedEvent event) {
        log.info("현재가 조회 이력. userId={}, stockCode={}, requestedAt={}",
                event.payload().userId(),
                event.payload().stockCode(),
                event.payload().requestedAt());
    }
}
