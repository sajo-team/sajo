package com.sajo.market_service.market.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * {@code MarketPriceEventProducer} 전용 발행 executor(코드 리뷰 반영, #239).
 *
 * <p>{@code KafkaTemplate#send()}는 브로커 ack 대기 자체는 {@code CompletableFuture}라 논블로킹이지만,
 * 토픽 메타데이터가 로컬에 캐시돼 있지 않은 경우(신규 토픽 최초 발행, 메타데이터 만료, 브로커 응답 지연 등)에는 {@code send()} 호출 자체가
 * {@code max.block.ms}(기본 60s)까지 호출 스레드에서 동기적으로 블로킹되는 Kafka 클라이언트 자체의 특성이 있다.
 *
 * {@code MarketPriceEventProducer}는 KIS WebSocket 메시지 수신 스레드에서 직접 호출되므로,
 * {@code send()} 호출 자체를 이 전용 단일 스레드로 위임해 그 블로킹이 WebSocket 수신 스레드로 전혀 전파되지 않도록 격리한다.</p>
 */
@Configuration
public class MarketKafkaProducerConfig {

    /** 발행 전용 단일 데몬 스레드. 이벤트 발행량이 크지 않아 하나로 충분하다. */
    @Bean(destroyMethod = "shutdown")
    Executor marketPriceEventPublishExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "market-price-event-publisher");
            thread.setDaemon(true);
            return thread;
        });
    }
}
