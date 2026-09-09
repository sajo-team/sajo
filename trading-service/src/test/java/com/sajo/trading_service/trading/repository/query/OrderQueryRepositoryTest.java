package com.sajo.trading_service.trading.repository.query;

import com.sajo.common.config.CommonJpaAuditingAutoConfiguration;
import com.sajo.trading_service.trading.domain.Order;
import com.sajo.trading_service.trading.domain.enums.OrderType;
import com.sajo.trading_service.trading.repository.command.OrderCommandRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@Import(CommonJpaAuditingAutoConfiguration.class)
class OrderQueryRepositoryTest {

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

        registry.add(
                "spring.flyway.schemas",
                () -> "trading"
        );

        registry.add(
                "spring.flyway.default-schema",
                () -> "trading"
        );
    }

    @Autowired
    private OrderQueryRepository orderQueryRepository;

    @Autowired
    private OrderCommandRepository orderCommandRepository;

    @Test
    @DisplayName(
            "매수 체결 수량이 남아 있으면 미청산 수량이 존재한다"
    )
    void existsOpenPositionWhenBuyQuantityRemains() {
        // given
        UUID userId = UUID.randomUUID();
        UUID autoTradingId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        Order buyOrder = Order.create(
                userId,
                autoTradingId,
                strategyId,
                UUID.randomUUID(),
                "005930",
                OrderType.BUY,
                70_000L,
                10
        );

        buyOrder.startProcessing();
        buyOrder.accept("BUY-001");
        buyOrder.applyFill(
                10,
                0
        );

        orderCommandRepository.saveAndFlush(
                buyOrder
        );

        // when
        boolean result =
                orderQueryRepository
                        .existsOpenPositionByAutoTradingId(
                                autoTradingId
                        );

        // then
        assertThat(result)
                .isTrue();
    }

    @Test
    @DisplayName(
            "매수 체결 수량을 전량 매도하면 미청산 수량이 존재하지 않는다"
    )
    void doesNotExistOpenPositionWhenFullySold() {
        // given
        UUID userId = UUID.randomUUID();
        UUID autoTradingId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        Order buyOrder = Order.create(
                userId,
                autoTradingId,
                strategyId,
                UUID.randomUUID(),
                "005930",
                OrderType.BUY,
                70_000L,
                10
        );

        buyOrder.startProcessing();
        buyOrder.accept("BUY-001");
        buyOrder.applyFill(
                10,
                0
        );

        Order sellOrder = Order.create(
                userId,
                autoTradingId,
                strategyId,
                UUID.randomUUID(),
                "005930",
                OrderType.SELL,
                71_000L,
                10
        );

        sellOrder.startProcessing();
        sellOrder.accept("SELL-001");
        sellOrder.applyFill(
                10,
                0
        );

        orderCommandRepository.saveAndFlush(
                buyOrder
        );

        orderCommandRepository.saveAndFlush(
                sellOrder
        );

        // when
        boolean result =
                orderQueryRepository
                        .existsOpenPositionByAutoTradingId(
                                autoTradingId
                        );

        // then
        assertThat(result)
                .isFalse();
    }

    @Test
    @DisplayName(
            "여러 종목 중 하나라도 미청산 수량이 있으면 미청산 상태로 판단한다"
    )
    void existsOpenPositionWhenAnyStockRemains() {
        // given
        UUID userId = UUID.randomUUID();
        UUID autoTradingId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        Order samsungBuy = Order.create(
                userId,
                autoTradingId,
                strategyId,
                UUID.randomUUID(),
                "005930",
                OrderType.BUY,
                70_000L,
                10
        );

        samsungBuy.startProcessing();
        samsungBuy.accept("BUY-001");
        samsungBuy.applyFill(
                10,
                0
        );

        Order samsungSell = Order.create(
                userId,
                autoTradingId,
                strategyId,
                UUID.randomUUID(),
                "005930",
                OrderType.SELL,
                71_000L,
                10
        );

        samsungSell.startProcessing();
        samsungSell.accept("SELL-001");
        samsungSell.applyFill(
                10,
                0
        );

        Order hynixBuy = Order.create(
                userId,
                autoTradingId,
                strategyId,
                UUID.randomUUID(),
                "000660",
                OrderType.BUY,
                200_000L,
                5
        );

        hynixBuy.startProcessing();
        hynixBuy.accept("BUY-002");
        hynixBuy.applyFill(
                5,
                0
        );

        Order hynixSell = Order.create(
                userId,
                autoTradingId,
                strategyId,
                UUID.randomUUID(),
                "000660",
                OrderType.SELL,
                201_000L,
                2
        );

        hynixSell.startProcessing();
        hynixSell.accept("SELL-002");
        hynixSell.applyFill(
                2,
                0
        );

        orderCommandRepository.saveAndFlush(
                samsungBuy
        );

        orderCommandRepository.saveAndFlush(
                samsungSell
        );

        orderCommandRepository.saveAndFlush(
                hynixBuy
        );

        orderCommandRepository.saveAndFlush(
                hynixSell
        );

        // when
        boolean result =
                orderQueryRepository
                        .existsOpenPositionByAutoTradingId(
                                autoTradingId
                        );

        // then
        assertThat(result)
                .isTrue();
    }

    @Test
    @DisplayName("진행 중인 주문이 존재하면 true를 반환한다")
    void existsActiveOrderByAutoTradingId() {
        // given
        UUID userId = UUID.randomUUID();
        UUID autoTradingId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        Order order = Order.create(
                userId,
                autoTradingId,
                strategyId,
                UUID.randomUUID(),
                "005930",
                OrderType.BUY,
                70_000L,
                10
        );

        // Order.create() 직후 상태 = REQUESTED
        orderCommandRepository.saveAndFlush(order);

        // when
        boolean result =
                orderQueryRepository
                        .existsActiveOrderByAutoTradingId(autoTradingId);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("종료된 주문만 존재하면 진행 중 주문이 없는 것으로 판단한다")
    void doesNotExistActiveOrderWhenOrderIsFilled() {
        // given
        UUID userId = UUID.randomUUID();
        UUID autoTradingId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        Order order = Order.create(
                userId,
                autoTradingId,
                strategyId,
                UUID.randomUUID(),
                "005930",
                OrderType.BUY,
                70_000L,
                10
        );

        order.startProcessing();
        order.accept("ORDER-001");
        order.applyFill(10, 0);

        orderCommandRepository.saveAndFlush(order);

        // when
        boolean result =
                orderQueryRepository
                        .existsActiveOrderByAutoTradingId(autoTradingId);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("다른 AutoTrading의 진행 중 주문은 조회하지 않는다")
    void doesNotExistActiveOrderForDifferentAutoTrading() {
        // given
        UUID userId = UUID.randomUUID();
        UUID targetAutoTradingId = UUID.randomUUID();
        UUID otherAutoTradingId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        Order order = Order.create(
                userId,
                otherAutoTradingId,
                strategyId,
                UUID.randomUUID(),
                "005930",
                OrderType.BUY,
                70_000L,
                10
        );

        orderCommandRepository.saveAndFlush(order);

        // when
        boolean result =
                orderQueryRepository
                        .existsActiveOrderByAutoTradingId(
                                targetAutoTradingId
                        );

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("논리 삭제된 진행 중 주문은 조회하지 않는다")
    void doesNotExistActiveOrderWhenDeleted() {
        UUID userId = UUID.randomUUID();
        UUID autoTradingId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        Order order = Order.create(
                userId,
                autoTradingId,
                strategyId,
                UUID.randomUUID(),
                "005930",
                OrderType.BUY,
                70_000L,
                10
        );

        order.softDelete(userId);

        orderCommandRepository.saveAndFlush(order);

        boolean result =
                orderQueryRepository
                        .existsActiveOrderByAutoTradingId(autoTradingId);

        assertThat(result).isFalse();
    }
}