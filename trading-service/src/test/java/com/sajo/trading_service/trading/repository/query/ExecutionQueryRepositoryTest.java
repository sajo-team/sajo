package com.sajo.trading_service.trading.repository.query;

import com.sajo.common.config.CommonJpaAuditingAutoConfiguration;
import com.sajo.trading_service.trading.domain.Execution;
import com.sajo.trading_service.trading.domain.Order;
import com.sajo.trading_service.trading.domain.enums.OrderType;
import com.sajo.trading_service.trading.repository.command.OrderCommandRepository;
import com.sajo.trading_service.trading.repository.query.projection.ExecutionAdminQueryProjection;
import com.sajo.trading_service.trading.repository.query.projection.ExecutionQueryProjection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@Import(CommonJpaAuditingAutoConfiguration.class)
class ExecutionQueryRepositoryTest {

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
    private ExecutionQueryRepository executionQueryRepository;

    @Autowired
    private OrderCommandRepository orderCommandRepository;

    @Autowired
    private OrderQueryRepository orderQueryRepository;

    @Test
    @DisplayName("autoTradingId로 체결을 조회하면 주문 추적 정보가 Projection에 매핑된다")
    void findByUserId_withAutoTradingId() {
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
                259_000L,
                3
        );

        order.startProcessing();
        order.accept("0000016037");

        orderCommandRepository.saveAndFlush(order);

        Execution execution = Execution.create(
                order.getId(),
                3,
                new BigDecimal("259000"),
                777_000L,
                0
        );

        executionQueryRepository.saveAndFlush(execution);

        // when
        var result =
                executionQueryRepository.findByUserId(
                        userId,
                        null,
                        autoTradingId,
                        null,
                        PageRequest.of(0, 10)
                );

        // then
        assertThat(result.getContent()).hasSize(1);

        ExecutionQueryProjection projection =
                result.getContent().get(0);

        assertThat(projection.executionId())
                .isEqualTo(execution.getId());

        assertThat(projection.orderId())
                .isEqualTo(order.getId());

        assertThat(projection.autoTradingId())
                .isEqualTo(autoTradingId);

        assertThat(projection.strategyId())
                .isEqualTo(strategyId);

        assertThat(projection.brokerOrderNo())
                .isEqualTo("0000016037");

        assertThat(projection.executedQuantity())
                .isEqualTo(3);

        assertThat(projection.averageExecutionPrice())
                .isEqualByComparingTo("259000");
    }

    @Test
    @DisplayName("관리자는 userId와 stockCode, orderType 조건으로 체결 내역을 조회할 수 있다")
    void findAllForAdminWithConditions() {

        // given
        UUID userId = UUID.randomUUID();
        UUID autoTradingId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();
        UUID signalId = UUID.randomUUID();

        Order order = Order.create(
                userId,
                autoTradingId,
                strategyId,
                signalId,
                "005930",
                OrderType.SELL,
                70_000L,
                3
        );

        orderQueryRepository.saveAndFlush(order);

        Execution execution = Execution.create(
                order.getId(),
                3,
                BigDecimal.valueOf(70_000),
                210_000L,
                0
        );

        executionQueryRepository.saveAndFlush(execution);

        // when
        Page<ExecutionAdminQueryProjection> result =
                executionQueryRepository.findAllForAdmin(
                        userId,
                        null,
                        null,
                        null,
                        "005930",
                        OrderType.SELL,
                        PageRequest.of(0, 10)
                );

        // then
        assertThat(result.getTotalElements())
                .isEqualTo(1);

        ExecutionAdminQueryProjection projection =
                result.getContent().get(0);

        assertThat(projection.userId())
                .isEqualTo(userId);

        assertThat(projection.stockCode())
                .isEqualTo("005930");

        assertThat(projection.orderType())
                .isEqualTo(OrderType.SELL);

        assertThat(projection.executedQuantity())
                .isEqualTo(3);

        assertThat(projection.remainingQuantity())
                .isEqualTo(0);
    }
}