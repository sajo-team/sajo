package com.sajo.trading_service.trading.repository.command;

import com.sajo.common.config.CommonJpaAuditingAutoConfiguration;
import com.sajo.trading_service.trading.domain.AutoTrading;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@Import(CommonJpaAuditingAutoConfiguration.class)
class AutoTradingCommandRepositoryTest {

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
    private AutoTradingCommandRepository autoTradingCommandRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName(
            "동일 사용자와 전략의 AutoTrading을 동시에 생성하면 active row는 하나만 저장된다"
    )
    void concurrentCreateAutoTrading() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        assertPartialUniqueIndexExists();

        ExecutorService executorService =
                Executors.newFixedThreadPool(2);

        CountDownLatch readyLatch =
                new CountDownLatch(2);

        CountDownLatch startLatch =
                new CountDownLatch(1);

        try {
            Future<Boolean> first =
                    executorService.submit(() -> {
                        readyLatch.countDown();
                        startLatch.await();

                        return saveAutoTrading(
                                userId,
                                strategyId
                        );
                    });

            Future<Boolean> second =
                    executorService.submit(() -> {
                        readyLatch.countDown();
                        startLatch.await();

                        return saveAutoTrading(
                                userId,
                                strategyId
                        );
                    });

            /*
             * 두 작업 스레드가 모두 준비될 때까지 기다린다.
             */
            readyLatch.await();

            // when
            startLatch.countDown();

            boolean firstResult = first.get();
            boolean secondResult = second.get();


            Integer activeRowCount = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM trading.p_auto_tradings
                    WHERE user_id = ?
                      AND strategy_id = ?
                      AND deleted_at IS NULL
                    """,
                    Integer.class,
                    userId,
                    strategyId
            );

            assertThat(firstResult ^ secondResult)
                    .as(
                            "firstResult=%s, secondResult=%s, activeRowCount=%s",
                            firstResult,
                            secondResult,
                            activeRowCount
                    )
                    .isTrue();

            assertThat(activeRowCount)
                    .as(
                            "동시 생성 후 active AutoTrading row 수 - firstResult=%s, secondResult=%s",
                            firstResult,
                            secondResult
                    )
                    .isEqualTo(1);

        } finally {
            executorService.shutdownNow();
        }
    }

    private boolean saveAutoTrading(
            UUID userId,
            UUID strategyId
    ) {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        transactionTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );

        try {
            transactionTemplate.executeWithoutResult(status -> {
                AutoTrading autoTrading =
                        AutoTrading.create(
                                userId,
                                strategyId
                        );

                autoTradingCommandRepository.saveAndFlush(autoTrading);
            });

            return true;

        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }

    private void assertPartialUniqueIndexExists() {
        Integer indexCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM pg_indexes
                        WHERE schemaname = 'trading'
                          AND tablename = 'p_auto_tradings'
                          AND indexname =
                              'uq_auto_trading_active_user_strategy'
                        """,
                        Integer.class
                );

        assertThat(indexCount)
                .isEqualTo(1);
    }
}