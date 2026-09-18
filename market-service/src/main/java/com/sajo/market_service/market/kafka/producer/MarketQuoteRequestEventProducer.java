package com.sajo.market_service.market.kafka.producer;

import com.sajo.market_service.market.kafka.dto.MarketQuoteRequestedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * {@code market.quote.requested} 토픽에 {@link MarketQuoteRequestedEvent}를 발행한다(#248).
 * 같은 종목의 조회 이력을 한 파티션에 모아 컨슈머가 순서대로 처리할 수 있도록 stockCode를 메시지 키로 사용한다.
 *
 * <p>이 메서드는 현재가 조회 API(Controller)에서, 응답을 반환하기 직전에 호출된다.
 * {@code send()} 호출 자체가 블로킹될 가능성을 응답 경로에서 완전히 격리하기 위해
 * 전용 executor로 위임한다 ({@link MarketQuoteRequestEventProducer}의 실제 발행 이유는
 * {@code MarketQuoteRequestEventProducerConfig} 참고).</p>
 *
 * <p>발행 실패는 이 클래스가 흡수해 로그만 남기고 호출부로 전파하지 않는다 — 조회 이력 기록
 * 실패가 현재가 조회 응답 자체를 실패시켜서는 안 된다.</p>
 */
@Slf4j
@Component
public class MarketQuoteRequestEventProducer {

    private static final String TOPIC = "market.quote.requested";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final Executor publishExecutor;

    public MarketQuoteRequestEventProducer(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Qualifier("marketQuoteRequestEventPublishExecutor") Executor publishExecutor
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.publishExecutor = publishExecutor;
    }

    public void publish(MarketQuoteRequestedEvent event) {
        String stockCode = event.payload().stockCode();
        try {
            publishExecutor.execute(() -> send(stockCode, event));
        } catch (RejectedExecutionException exception) {
            // 발행 큐(marketQuoteRequestEventPublishExecutor)가 가득 찼을 때의 방어선(#248).
            // 큐의 RejectedExecutionHandler가 이미 로그를 남기고 조용히 버리도록 설정되어 있지만,
            // 설정이 바뀌어 예외가 던져지는 경우에도 호출 스레드(현재가 조회 응답 경로)로는
            // 전파되지 않도록 이중으로 막는다.
            log.warn("MarketQuoteRequestedEvent 발행 큐가 가득 차 이번 이벤트를 스킵합니다. stockCode={}",
                    stockCode);
        }
    }

    private void send(String stockCode, MarketQuoteRequestedEvent event) {
        try {
            kafkaTemplate.send(TOPIC, stockCode, event)
                    .whenComplete((result, throwable) -> {
                        if (throwable != null) {
                            log.warn("MarketQuoteRequestedEvent Kafka 발행에 실패했습니다. stockCode={}",
                                    stockCode, throwable);
                        }
                    });
        } catch (RuntimeException exception) {
            // kafkaTemplate.send() 자체가 실패하는 경우(메타데이터 조회 타임아웃, 직렬화 실패 등).
            // 전용 executor 스레드에서 발생하므로 여기서 예외가 나도 요청 스레드에는 영향이 없다.
            log.warn("MarketQuoteRequestedEvent Kafka 발행 요청 자체가 실패했습니다. stockCode={}",
                    stockCode, exception);
        }
    }
}
