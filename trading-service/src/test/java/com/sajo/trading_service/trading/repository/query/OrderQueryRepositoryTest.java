package com.sajo.trading_service.trading.repository.query;

import com.sajo.common.config.CommonJpaAuditingAutoConfiguration;
import com.sajo.trading_service.trading.controller.dto.request.OrderSearchCondition;
import com.sajo.trading_service.trading.domain.Order;
import com.sajo.trading_service.trading.domain.enums.OrderStatus;
import com.sajo.trading_service.trading.domain.enums.OrderType;
import com.sajo.trading_service.trading.repository.command.OrderCommandRepository;
import com.sajo.trading_service.trading.repository.query.specification.OrderSpecifications;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    @Test
    @DisplayName("autoTradingId와 status 조건을 동시에 적용해 주문을 조회한다")
    void findAll_withAutoTradingIdAndStatus() {
        // given
        UUID userId = UUID.randomUUID();
        UUID autoTradingId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        // 조회되어야 하는 주문
        Order matchedOrder = Order.create(
                userId,
                autoTradingId,
                strategyId,
                UUID.randomUUID(),
                "005930",
                OrderType.BUY,
                70_000L,
                10
        );

        matchedOrder.startProcessing();
        matchedOrder.accept("ORDER-001");
        matchedOrder.applyFill(10, 0);

        // 같은 AutoTrading이지만 상태가 REQUESTED
        Order differentStatusOrder = Order.create(
                userId,
                autoTradingId,
                strategyId,
                UUID.randomUUID(),
                "005930",
                OrderType.BUY,
                70_000L,
                10
        );

        // FILLED지만 다른 AutoTrading
        Order differentAutoTradingOrder = Order.create(
                userId,
                UUID.randomUUID(),
                strategyId,
                UUID.randomUUID(),
                "005930",
                OrderType.BUY,
                70_000L,
                10
        );

        differentAutoTradingOrder.startProcessing();
        differentAutoTradingOrder.accept("ORDER-002");
        differentAutoTradingOrder.applyFill(10, 0);

        orderCommandRepository.saveAndFlush(matchedOrder);
        orderCommandRepository.saveAndFlush(differentStatusOrder);
        orderCommandRepository.saveAndFlush(differentAutoTradingOrder);

        OrderSearchCondition condition =
                new OrderSearchCondition(
                        autoTradingId,
                        null,
                        OrderStatus.FILLED,
                        null,
                        null
                );

        // when
        var result =
                orderQueryRepository.findAll(
                        OrderSpecifications.withCondition(
                                userId,
                                condition
                        ),
                        PageRequest.of(0, 10)
                );

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId())
                .isEqualTo(matchedOrder.getId());
    }

    @Test
    @DisplayName("AutoTrading별 가장 최근 주문 한 건씩 조회한다")
    void findLatestOrdersByAutoTradingIds() {
        // given
        UUID userId = UUID.randomUUID();

        UUID autoTradingId1 = UUID.randomUUID();
        UUID autoTradingId2 = UUID.randomUUID();

        UUID strategyId1 = UUID.randomUUID();
        UUID strategyId2 = UUID.randomUUID();

        Order firstOldOrder =
                Order.create(
                        userId,
                        autoTradingId1,
                        strategyId1,
                        UUID.randomUUID(),
                        "005930",
                        OrderType.BUY,
                        70_000L,
                        1
                );

        Order firstLatestOrder =
                Order.create(
                        userId,
                        autoTradingId1,
                        strategyId1,
                        UUID.randomUUID(),
                        "005930",
                        OrderType.BUY,
                        71_000L,
                        1
                );

        Order secondLatestOrder =
                Order.create(
                        userId,
                        autoTradingId2,
                        strategyId2,
                        UUID.randomUUID(),
                        "000660",
                        OrderType.SELL,
                        120_000L,
                        1
                );

        orderCommandRepository.saveAndFlush(firstOldOrder);

        /*
         * createdAt 기준 최신 순을 확실하게 만들기 위해
         * 첫 주문 저장 후 다음 주문을 저장한다.
         */
        orderCommandRepository.saveAndFlush(firstLatestOrder);
        orderCommandRepository.saveAndFlush(secondLatestOrder);

        // when
        List<Order> result =
                orderQueryRepository.findLatestOrdersByAutoTradingIds(
                        List.of(
                                autoTradingId1,
                                autoTradingId2
                        )
                );

        // then
        assertThat(result)
                .hasSize(2);

        assertThat(result)
                .extracting(Order::getId)
                .containsExactlyInAnyOrder(
                        firstLatestOrder.getId(),
                        secondLatestOrder.getId()
                );

        assertThat(result)
                .extracting(Order::getAutoTradingId)
                .containsExactlyInAnyOrder(
                        autoTradingId1,
                        autoTradingId2
                );
    }

    @Test
    @DisplayName("동일 생성시각 주문이 존재하면 단건과 목록 조회 모두 ID DESC 기준으로 동일한 최신 주문을 반환한다")
    void findLatestOrderUsesIdDescAsTieBreaker() {
        // given
        UUID userId = UUID.randomUUID();
        UUID autoTradingId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        Order firstOrder =
                Order.create(
                        userId,
                        autoTradingId,
                        strategyId,
                        UUID.randomUUID(),
                        "005930",
                        OrderType.BUY,
                        70_000L,
                        1
                );

        Order secondOrder =
                Order.create(
                        userId,
                        autoTradingId,
                        strategyId,
                        UUID.randomUUID(),
                        "005930",
                        OrderType.BUY,
                        71_000L,
                        1
                );

        orderCommandRepository.saveAndFlush(firstOrder);
        orderCommandRepository.saveAndFlush(secondOrder);

        Instant sameCreatedAt =
                Instant.parse("2026-09-15T00:00:00Z");

        jdbcTemplate.update(
                """
                UPDATE trading.p_orders
                SET created_at = ?
                WHERE id IN (?, ?)
                """,
                Timestamp.from(sameCreatedAt),
                firstOrder.getId(),
                secondOrder.getId()
        );

        /*
         * PostgreSQL의 실제 UUID DESC 기준으로
         * 기대되는 주문 ID를 구한다.
         */
        UUID expectedLatestOrderId =
                jdbcTemplate.queryForObject(
                        """
                        SELECT id
                        FROM trading.p_orders
                        WHERE id IN (?, ?)
                        ORDER BY id DESC
                        LIMIT 1
                        """,
                        UUID.class,
                        firstOrder.getId(),
                        secondOrder.getId()
                );

        // when
        Order detailLatestOrder =
                orderQueryRepository
                        .findFirstByAutoTradingIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                                autoTradingId
                        )
                        .orElseThrow();

        List<Order> listLatestOrders =
                orderQueryRepository
                        .findLatestOrdersByAutoTradingIds(
                                List.of(autoTradingId)
                        );

        // then
        assertThat(detailLatestOrder.getId())
                .isEqualTo(expectedLatestOrderId);

        assertThat(listLatestOrders)
                .hasSize(1);

        assertThat(listLatestOrders.getFirst().getId())
                .isEqualTo(expectedLatestOrderId);

        assertThat(detailLatestOrder.getId())
                .isEqualTo(listLatestOrders.getFirst().getId());
    }

    @Test
    @DisplayName("TIMEOUT 주문 중 재조정 횟수가 최대 횟수 미만인 주문만 조회한다")
    void findStaleTimeoutOrderIds_onlyBelowMaxRetryCount() {
        // given
        Instant oldTime =
                Instant.now().minus(
                        10,
                        ChronoUnit.MINUTES
                );

        Order retryCount2Order =
                createTimeoutOrderWithRetryCount(
                        2,
                        oldTime
                );

        Order retryCount3Order =
                createTimeoutOrderWithRetryCount(
                        3,
                        oldTime
                );

        // when
        List<UUID> result =
                orderQueryRepository.findStaleTimeoutOrderIds(
                        Instant.now().minus(
                                1,
                                ChronoUnit.MINUTES
                        ),
                        3
                );

        // then
        assertThat(result)
                .contains(retryCount2Order.getId())
                .doesNotContain(retryCount3Order.getId());
    }

    @Test
    @DisplayName("재조정 횟수가 최대 횟수 미만이어도 cutoff보다 최신 TIMEOUT 주문은 조회하지 않는다")
    void findStaleTimeoutOrderIds_excludesRecentOrder() {
        // given
        Order recentOrder =
                createTimeoutOrderWithRetryCount(
                        1,
                        Instant.now()
                );

        // when
        List<UUID> result =
                orderQueryRepository.findStaleTimeoutOrderIds(
                        Instant.now().minus(
                                1,
                                ChronoUnit.MINUTES
                        ),
                        3
                );

        // then
        assertThat(result)
                .doesNotContain(recentOrder.getId());
    }

    private Order createTimeoutOrderWithRetryCount(
            int retryCount,
            Instant updatedAt
    ) {
        Order order = Order.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "005930",
                OrderType.BUY,
                70_000L,
                10
        );

        order.startProcessing();

        for (int i = 0; i < retryCount; i++) {
            order.recordReconciliationFailure(
                    3,
                    "KIS_RECONCILIATION_EXHAUSTED",
                    "KIS 주문 조회로 주문 상태를 확정하지 못했습니다."
            );
        }

        orderCommandRepository.saveAndFlush(order);

        jdbcTemplate.update(
                """
                UPDATE trading.p_orders
                SET updated_at = ?
                WHERE id = ?
                """,
                Timestamp.from(updatedAt),
                order.getId()
        );

        return order;
    }
}