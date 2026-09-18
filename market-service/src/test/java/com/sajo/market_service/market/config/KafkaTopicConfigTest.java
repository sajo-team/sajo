package com.sajo.market_service.market.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code KafkaTopicConfig}가 선언한 세 토픽 모두 파티션 3개로 생성되도록 요청하는지
 * 검증한다(#263). 이 값이 흐트러지면 {@code MarketQuoteRequestConsumer}/{@code TradingSignalConsumer}의
 * {@code concurrency = "3"}이 실제 파티션 수보다 커져, 남는 컨슈머 스레드가 할당받을 파티션이
 * 없어 idle 상태로 남는 불일치가 생긴다.
 */
class KafkaTopicConfigTest {

    private static final int EXPECTED_PARTITION_COUNT = 3;
    private static final short EXPECTED_REPLICATION_FACTOR = 1;

    private final KafkaTopicConfig config = new KafkaTopicConfig();

    static Stream<NewTopic> topics() {
        KafkaTopicConfig config = new KafkaTopicConfig();
        return Stream.of(
                config.marketQuoteRequestedTopic(),
                config.marketPriceUpdatedTopic(),
                config.tradingSignalGeneratedTopic()
        );
    }

    @ParameterizedTest
    @MethodSource("topics")
    void everyTopicHasThreePartitionsAndReplicationFactorOne(NewTopic topic) {
        assertThat(topic.numPartitions()).isEqualTo(EXPECTED_PARTITION_COUNT);
        assertThat(topic.replicationFactor()).isEqualTo(EXPECTED_REPLICATION_FACTOR);
    }

    @Test
    void topicNamesMatchWhatProducersActuallySendTo() {
        assertThat(config.marketQuoteRequestedTopic().name()).isEqualTo("market.quote.requested");
        assertThat(config.marketPriceUpdatedTopic().name()).isEqualTo("market.price.updated");
        assertThat(config.tradingSignalGeneratedTopic().name()).isEqualTo("trading.signal.generated");
    }
}
