package com.sajo.market_service.strategy.kafka.consumer;

import com.sajo.common.config.CommonJpaAuditingAutoConfiguration;
import com.sajo.market_service.market.domain.PriceSource;
import com.sajo.market_service.market.kafka.dto.MarketPricePayload;
import com.sajo.market_service.market.kafka.dto.MarketPriceUpdatedEvent;
import com.sajo.market_service.strategy.cache.SignalStateStore;
import com.sajo.market_service.strategy.domain.Strategy;
import com.sajo.market_service.strategy.domain.StrategyStatus;
import com.sajo.market_service.strategy.kafka.dto.TradingSignalGeneratedEvent;
import com.sajo.market_service.strategy.kafka.producer.TradingSignalProducer;
import com.sajo.market_service.strategy.repository.query.StrategyQueryRepository;
import com.sajo.market_service.strategy.service.command.StrategyEvaluationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.stereotype.Component;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * 실제 Kafka(EmbeddedKafka)·Redis(Testcontainers) 위에서 strategy가 소유한 계약 지점인
 * {@code market.price.updated} 이벤트를 직접 발행해, 실제 {@link MarketPriceEvaluationConsumer}
 * (@KafkaListener) → {@link StrategyEvaluationService} → {@link SignalStateStore}(Redis Lua) →
 * {@link TradingSignalProducer} → {@code trading.signal.generated} 발행까지 전체 체인을 검증한다.
 * WebSocket 파싱(market 소유 코드)은 포함하지 않는다 — 장 시간과 무관하게 반복 실행 가능한
 * "Docker 기반 E2E" 자동화 테스트다. DB는 사용하지 않고 {@link StrategyQueryRepository}만 목으로
 * 대체해, 순수 Kafka/Redis 경계 검증에 집중한다.
 */
@Testcontainers
@EnabledIfDockerAvailable
@SpringBootTest(
        classes = MarketPriceEvaluationConsumerIntegrationTest.TestConfig.class,
        properties = "eureka.client.enabled=false"
)
@EmbeddedKafka(partitions = 3, topics = {"market.price.updated", "trading.signal.generated"})
@DisplayName("MarketPriceEvaluationConsumer 실제 Kafka/Redis 통합 테스트")
class MarketPriceEvaluationConsumerIntegrationTest {

    private static final String STOCK_CODE = "005930";

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        // 컨슈머 그룹 rebalance가 발행보다 늦게 끝나도 놓치지 않도록 처음부터 읽게 한다.
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
    }

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private TradingSignalRecordingListener recordingListener;

    @MockitoBean
    private StrategyQueryRepository strategyQueryRepository;

    @Test
    @DisplayName("market.price.updated 발행 → 전략 평가 → trading.signal.generated 발행까지 실제로 흐른다")
    void publishesMarketPriceEventAndReceivesGeneratedSignal() throws InterruptedException {
        Strategy strategy = Strategy.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                STOCK_CODE,
                "E2E 테스트 전략",
                70_000L,
                80_000L,
                new BigDecimal("5.0000"),
                null,
                3_000_000L,
                100_000L,
                null,
                null,
                null
        );
        // 리포지토리를 목으로 대체해 실제로 저장하지 않으므로, @GeneratedValue로 채워질 id를 직접 넣어준다.
        UUID strategyId = UUID.randomUUID();
        org.springframework.test.util.ReflectionTestUtils.setField(strategy, "id", strategyId);
        given(strategyQueryRepository.findAllByStockCodeAndStatusAndDeletedAtIsNull(STOCK_CODE, StrategyStatus.ACTIVE))
                .willReturn(List.of(strategy));

        MarketPricePayload payload = new MarketPricePayload(
                STOCK_CODE, 65_000L, -1_000L, new BigDecimal("-1.51"), 12_345L,
                Instant.now(), PriceSource.WEBSOCKET
        );
        MarketPriceUpdatedEvent event = new MarketPriceUpdatedEvent(
                UUID.randomUUID(), "MARKET_PRICE_UPDATED", 1, Instant.now(), payload
        );

        kafkaTemplate.send("market.price.updated", STOCK_CODE, event);

        TradingSignalGeneratedEvent received = recordingListener.poll();

        assertThat(received).isNotNull();
        assertThat(received.payload().strategyId()).isEqualTo(strategy.getId());
        assertThat(received.payload().stockCode()).isEqualTo(STOCK_CODE);
        assertThat(received.payload().signalType().name()).isEqualTo("BUY");
    }

    @Component
    static class TradingSignalRecordingListener {
        private final BlockingQueue<TradingSignalGeneratedEvent> received = new LinkedBlockingQueue<>();

        @KafkaListener(id = "test-signal-verifier", topics = "trading.signal.generated", groupId = "test-signal-verifier-group")
        public void onMessage(TradingSignalGeneratedEvent event) {
            received.add(event);
        }

        TradingSignalGeneratedEvent poll() throws InterruptedException {
            return received.poll(15, TimeUnit.SECONDS);
        }
    }

    @Configuration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataJpaRepositoriesAutoConfiguration.class,
            CommonJpaAuditingAutoConfiguration.class
    })
    @Import({
            com.sajo.market_service.strategy.kafka.config.StrategyKafkaConfig.class,
            MarketPriceEvaluationConsumer.class,
            StrategyEvaluationService.class,
            SignalStateStore.class,
            TradingSignalProducer.class,
            TradingSignalRecordingListener.class
    })
    static class TestConfig {
    }
}
