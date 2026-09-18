package com.sajo.market_service.market.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * market-service가 발행하는 모든 Kafka 토픽의 파티션 수를 명시적으로 선언한다(#263).
 *
 * <p>기존에는 {@code NewTopic}/{@code KafkaAdmin} Bean이 하나도 없어 세 토픽 모두
 * 브로커의 {@code num.partitions} 기본값(1)으로 자동 생성되고 있었다. 파티션이 1개면
 * 컨슈머 그룹에 스레드를 몇 개 두든({@code @KafkaListener(concurrency=...)}) 실제로는
 * 파티션 1개를 두고 스레드 1개만 일하는 것과 동일해, 컨슈머 쪽 병렬 처리가 원천적으로
 * 불가능했다 — {@code GET /api/v1/market/quote}에서 Redis 락 경합을 single-flight로
 * 완화한 것과 같은 종류의 문제(#248, #259~#261)가 Kafka 토픽 단에도 그대로 잠재해 있었다.</p>
 *
 * <p>파티션 수는 3으로 통일한다. 현재 단일 브로커(KAFKA_NODE_ID=1) 구성이라 파티션을
 * 여러 브로커에 분산하는 이점은 없지만, 파티션 수는 "컨슈머 그룹 내 동시에 일할 수 있는
 * 최대 스레드/인스턴스 수"를 결정하는 상한이므로, 브로커 대수와 무관하게 지금 단계에서
 * 미리 여유를 확보해 둔다. 3은 부트캠프 규모의 트래픽에서 컨슈머 병렬도를 확보하면서도
 * 파티션 과다로 인한 프로듀서/브로커 오버헤드(파일 핸들, 컨트롤러 메타데이터 등)를
 * 불필요하게 늘리지 않는 선에서 고른 값이다.</p>
 *
 * <p>세 토픽 모두 프로듀서가 종목코드(stockCode) 또는 전략/시그널 ID를 메시지 키로 사용하므로
 * ({@link com.sajo.market_service.market.kafka.producer.MarketQuoteRequestEventProducer},
 * {@link com.sajo.market_service.market.kafka.producer.MarketPriceEventProducer},
 * {@link com.sajo.market_service.strategy.kafka.producer.TradingSignalProducer}),
 * 파티션을 늘려도 같은 키는 항상 같은 파티션으로 가는 Kafka 기본 파티셔너 특성상
 * 키 단위 순서 보장은 그대로 유지된다.</p>
 */
@Configuration
public class KafkaTopicConfig {

    private static final int PARTITION_COUNT = 3;
    private static final short REPLICATION_FACTOR = 1;

    @Bean
    public NewTopic marketQuoteRequestedTopic() {
        return TopicBuilder.name("market.quote.requested")
                .partitions(PARTITION_COUNT)
                .replicas(REPLICATION_FACTOR)
                .build();
    }

    @Bean
    public NewTopic marketPriceUpdatedTopic() {
        return TopicBuilder.name("market.price.updated")
                .partitions(PARTITION_COUNT)
                .replicas(REPLICATION_FACTOR)
                .build();
    }

    @Bean
    public NewTopic tradingSignalGeneratedTopic() {
        return TopicBuilder.name("trading.signal.generated")
                .partitions(PARTITION_COUNT)
                .replicas(REPLICATION_FACTOR)
                .build();
    }
}
