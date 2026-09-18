package com.sajo.trading_service.trading.service.command;

import com.sajo.common.config.CommonJpaAuditingAutoConfiguration;
import com.sajo.trading_service.trading.domain.AutoTrading;
import com.sajo.trading_service.trading.domain.Order;
import com.sajo.trading_service.trading.domain.TradingLimit;
import com.sajo.trading_service.trading.domain.enums.AutoTradingDirection;
import com.sajo.trading_service.trading.domain.enums.OrderType;
import com.sajo.trading_service.trading.kafka.dto.TradingSignalGeneratedEvent;
import com.sajo.trading_service.trading.kafka.dto.TradingSignalPayload;
import com.sajo.trading_service.trading.repository.command.AutoTradingCommandRepository;
import com.sajo.trading_service.trading.repository.command.OrderCommandRepository;
import com.sajo.trading_service.trading.repository.command.TradingLimitCommandRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@Import(CommonJpaAuditingAutoConfiguration.class)
class TradingSignalConcurrencyIntegrationTest {

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "spring.datasource.url",
                () -> postgres.getJdbcUrl() + "?currentSchema=trading"
        );

        registry.add(
                "spring.datasource.username",
                postgres::getUsername
        );

        registry.add(
                "spring.datasource.password",
                postgres::getPassword
        );

        registry.add(
                "spring.jpa.properties.hibernate.default_schema",
                () -> "trading"
        );

        registry.add(
                "spring.jpa.hibernate.ddl-auto",
                () -> "update"
        );
    }

    @Autowired
    private TradingSignalCommandService tradingSignalCommandService;

    @Autowired
    private AutoTradingCommandRepository autoTradingCommandRepository;

    @Autowired
    private TradingLimitCommandRepository tradingLimitCommandRepository;

    @Autowired
    private OrderCommandRepository orderCommandRepository;

    @Test
    @DisplayName(
            "동일 AutoTrading에 Signal이 동시에 들어와도 진행 중 Order는 하나만 생성된다"
    )
    void concurrentSignals_createOnlyOneOrder() throws Exception {

        // given
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        AutoTrading autoTrading =
                AutoTrading.create(
                        userId,
                        strategyId,
                        AutoTradingDirection.BOTH
                );

        autoTrading.update(
                true,
                AutoTradingDirection.BOTH
        );

        autoTradingCommandRepository.saveAndFlush(autoTrading);

        TradingLimit tradingLimit =
                TradingLimit.create(
                        userId,
                        10_000_000L,
                        10,
                        BigDecimal.valueOf(5.0)
                );

        tradingLimitCommandRepository.saveAndFlush(tradingLimit);

        TradingSignalGeneratedEvent firstEvent =
                createEvent(
                        UUID.randomUUID(),
                        userId,
                        strategyId,
                        OrderType.BUY
                );

        TradingSignalGeneratedEvent secondEvent =
                createEvent(
                        UUID.randomUUID(),
                        userId,
                        strategyId,
                        OrderType.BUY
                );

        CountDownLatch ready =
                new CountDownLatch(2);

        CountDownLatch start =
                new CountDownLatch(1);

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        // when
        Future<?> first =
                executor.submit(() -> {
                    ready.countDown();
                    start.await();

                    tradingSignalCommandService
                            .processSignal(firstEvent);

                    return null;
                });

        Future<?> second =
                executor.submit(() -> {
                    ready.countDown();
                    start.await();

                    tradingSignalCommandService
                            .processSignal(secondEvent);

                    return null;
                });

        ready.await();

        start.countDown();

        first.get();
        second.get();

        executor.shutdown();

        // then
        long orderCount =
                orderCommandRepository
                        .countByAutoTradingIdAndDeletedAtIsNull(
                                autoTrading.getId()
                        );

        assertThat(orderCount)
                .isEqualTo(1L);
    }

    @Test
    @DisplayName(
            "보유 포지션이 있는 상태에서 BUY와 SELL Signal이 동시에 들어오면 각각 Order가 생성된다"
    )
    void concurrentOppositeDirectionSignals_createTwoOrders() throws Exception {

        // given
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        AutoTrading autoTrading =
                AutoTrading.create(
                        userId,
                        strategyId,
                        AutoTradingDirection.BOTH
                );

        autoTrading.update(
                true,
                AutoTradingDirection.BOTH
        );

        autoTradingCommandRepository.saveAndFlush(autoTrading);

        TradingLimit tradingLimit =
                TradingLimit.create(
                        userId,
                        10_000_000L,
                        10,
                        BigDecimal.valueOf(5.0)
                );

        tradingLimitCommandRepository.saveAndFlush(tradingLimit);

        Order existingFilledBuyOrder =
                Order.create(
                        userId,
                        autoTrading.getId(),
                        strategyId,
                        UUID.randomUUID(),
                        "005930",
                        OrderType.BUY,
                        70_000L,
                        10
                );

        existingFilledBuyOrder.startProcessing();
        existingFilledBuyOrder.accept("EXISTING-BUY-001");
        existingFilledBuyOrder.applyFill(
                10,
                0
        );

        orderCommandRepository.saveAndFlush(
                existingFilledBuyOrder
        );

        TradingSignalGeneratedEvent buyEvent =
                createEvent(
                        UUID.randomUUID(),
                        userId,
                        strategyId,
                        OrderType.BUY
                );

        TradingSignalGeneratedEvent sellEvent =
                createEvent(
                        UUID.randomUUID(),
                        userId,
                        strategyId,
                        OrderType.SELL
                );

        CountDownLatch ready =
                new CountDownLatch(2);

        CountDownLatch start =
                new CountDownLatch(1);

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        // when
        Future<?> buyFuture =
                executor.submit(() -> {
                    ready.countDown();
                    start.await();

                    tradingSignalCommandService
                            .processSignal(buyEvent);

                    return null;
                });

        Future<?> sellFuture =
                executor.submit(() -> {
                    ready.countDown();
                    start.await();

                    tradingSignalCommandService
                            .processSignal(sellEvent);

                    return null;
                });

        ready.await();

        start.countDown();

        buyFuture.get();
        sellFuture.get();

        executor.shutdown();

        // then
        long orderCount =
                orderCommandRepository
                        .countByAutoTradingIdAndDeletedAtIsNull(
                                autoTrading.getId()
                        );

        assertThat(orderCount)
                .isEqualTo(3L);
    }

    private TradingSignalGeneratedEvent createEvent(
            UUID signalId,
            UUID userId,
            UUID strategyId,
            OrderType orderType
    ) {
        TradingSignalPayload payload =
                new TradingSignalPayload(
                        signalId,
                        strategyId,
                        userId,
                        "005930",
                        orderType,
                        70_000L,
                        300_000L,
                        "동시성 테스트"
                );

        return new TradingSignalGeneratedEvent(
                UUID.randomUUID(),
                "TRADING_SIGNAL_GENERATED",
                1,
                Instant.now(),
                userId,
                payload
        );
    }
}