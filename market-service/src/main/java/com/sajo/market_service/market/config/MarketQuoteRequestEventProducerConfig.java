package com.sajo.market_service.market.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * {@code MarketQuoteRequestEventProducer} 전용 발행 executor(#248).
 *
 * <p>현재가 조회 API(GET /api/v1/market/quote)는 JMeter 부하 테스트 대상 API다. 요청을 받은
 * Tomcat worker 스레드에서 {@code KafkaTemplate#send()}를 직접 호출하면, 토픽 메타데이터가
 * 로컬에 없는 경우(최초 발행, 메타데이터 만료 등) {@code max.block.ms}(기본 60s)까지 그 스레드가
 * 동기적으로 블로킹될 수 있다 — 이는 곧 응답 지연·처리량 저하로 직결된다.
 *
 * <p>{@code MarketKafkaProducerConfig}의 {@code marketPriceEventPublishExecutor}와 동일한 이유로
 * 발행 자체를 전용 스레드로 위임하되, 큐는 무제한이 아니라 {@link #QUEUE_CAPACITY}로 제한한다.
 * 이 Producer는 실제 체결 빈도로 자연스럽게 상한이 있는 {@code market.price.updated}와 달리,
 * 의도적으로 200/sec 이상까지 부하를 거는 API에 직결되어 있어 Kafka가 일시적으로 느려지면 큐가
 * 무한정 쌓여 힙 메모리를 압박할 수 있다(코드 리뷰 반영, #248). 큐가 가득 차면 새 이벤트는
 * 버리고 로그만 남긴다 — 조회 이력(감사성 로그) 유실은 감내 가능하지만, 응답 스레드가 이 때문에
 * 블로킹되거나 예외가 전파되는 것은 허용하지 않는다.</p>
 */
@Slf4j
@Configuration
public class MarketQuoteRequestEventProducerConfig {

    private static final int QUEUE_CAPACITY = 10_000;

    @Bean(destroyMethod = "shutdown")
    Executor marketQuoteRequestEventPublishExecutor() {
        return new ThreadPoolExecutor(
                1, 1,
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                runnable -> {
                    Thread thread = new Thread(runnable, "market-quote-request-event-publisher");
                    thread.setDaemon(true);
                    return thread;
                },
                (rejectedTask, executor) -> log.warn(
                        "조회 이력 발행 큐(capacity={})가 가득 차 이번 이벤트를 스킵합니다.", QUEUE_CAPACITY)
        );
    }
}
