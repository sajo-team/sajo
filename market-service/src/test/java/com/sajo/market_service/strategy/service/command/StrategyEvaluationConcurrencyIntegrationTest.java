package com.sajo.market_service.strategy.service.command;

import com.sajo.market_service.strategy.cache.SignalStateStore;
import com.sajo.market_service.strategy.client.user.AccountHoldingFeignClient;
import com.sajo.market_service.strategy.controller.dto.request.StrategyEvaluationRequest;
import com.sajo.market_service.strategy.domain.Strategy;
import com.sajo.market_service.strategy.domain.StrategyStatus;
import com.sajo.market_service.strategy.kafka.producer.TradingSignalProducer;
import com.sajo.market_service.strategy.repository.query.StrategyQueryRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

// 실제 Redis 컨테이너에 대고 Lua compare-and-set 기반 상태 선점이 동시 평가에서도
// 정확히 한 번만 성공하는지 검증한다. LoginAttemptServiceTest와 동일하게 Spring 컨텍스트
// 전체를 띄우지 않고 StringRedisTemplate만 직접 구성해 DB/Eureka 등과 무관하게 격리한다.
@Testcontainers
@EnabledIfDockerAvailable
@DisplayName("StrategyEvaluationService 동시 평가 시 실제 Redis 컨테이너 통합 테스트")
class StrategyEvaluationConcurrencyIntegrationTest {

    private static final String STOCK_CODE = "005930";
    private static final int CONCURRENT_REQUESTS = 20;

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
            .withExposedPorts(6379);

    @Test
    @DisplayName("서로 다른 sourceEventId로 같은 조건을 동시에 평가해도 Signal은 정확히 한 번만 발행된다")
    void publishesExactlyOnceUnderConcurrentEvaluation() throws InterruptedException {
        LettuceConnectionFactory connectionFactory =
                new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();

        StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();

        Strategy strategy = Strategy.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                STOCK_CODE,
                "동시성 테스트 전략",
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

        StrategyQueryRepository strategyQueryRepository = Mockito.mock(StrategyQueryRepository.class);
        Mockito.when(strategyQueryRepository.findAllByStockCodeAndStatusAndDeletedAtIsNull(STOCK_CODE, StrategyStatus.ACTIVE))
                .thenReturn(List.of(strategy));
        TradingSignalProducer tradingSignalProducer = Mockito.mock(TradingSignalProducer.class);
        AccountHoldingFeignClient accountHoldingFeignClient = Mockito.mock(AccountHoldingFeignClient.class);
        Mockito.when(accountHoldingFeignClient.getHoldingPosition(Mockito.any(), Mockito.anyString()))
                .thenThrow(new RuntimeException("포지션 미반영(테스트 기본값)"));

        StrategyEvaluationService strategyEvaluationService = new StrategyEvaluationService(
                strategyQueryRepository, tradingSignalProducer, redisTemplate, new SignalStateStore(redisTemplate),
                new SimpleMeterRegistry(), accountHoldingFeignClient
        );

        ExecutorService executorService = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        CountDownLatch readyLatch = new CountDownLatch(CONCURRENT_REQUESTS);
        CountDownLatch startLatch = new CountDownLatch(1);

        try {
            List<Future<?>> futures = IntStream.range(0, CONCURRENT_REQUESTS)
                    .mapToObj(i -> executorService.submit(() -> {
                        readyLatch.countDown();
                        try {
                            startLatch.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        StrategyEvaluationRequest request = new StrategyEvaluationRequest(
                                UUID.randomUUID(), STOCK_CODE, 65_000L, Instant.now()
                        );
                        strategyEvaluationService.evaluate(request);
                    }))
                    .collect(Collectors.toList());

            readyLatch.await();
            startLatch.countDown();

            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            executorService.shutdownNow();
        }

        verify(tradingSignalProducer, Mockito.times(1)).publish(any());
        assertThat(redisTemplate.opsForValue().get("strategy:evaluation:state:" + strategy.getId()))
                .isEqualTo("BUY");
        assertThat(redisTemplate.opsForValue().get("strategy:evaluation:entry-price:" + strategy.getId()))
                .isEqualTo("65000");
    }
}
