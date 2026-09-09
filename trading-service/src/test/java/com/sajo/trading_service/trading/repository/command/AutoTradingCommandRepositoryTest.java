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

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName(
            "논리 삭제된 AutoTrading과 동일한 사용자와 전략으로 다시 생성할 수 있다"
    )
    void recreateAutoTradingAfterSoftDelete() {
        // given
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            AutoTrading first =
                    AutoTrading.create(
                            userId,
                            strategyId
                    );

            autoTradingCommandRepository.saveAndFlush(first);

            first.softDelete(userId);

            autoTradingCommandRepository.saveAndFlush(first);
        });

        // when
        boolean created =
                saveAutoTrading(
                        userId,
                        strategyId
                );

        // then
        assertThat(created)
                .isTrue();

        Integer activeRowCount =
                jdbcTemplate.queryForObject(
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

        assertThat(activeRowCount)
                .isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName(
            "AutoTrading 삭제와 Signal 조회가 동시에 발생하면 삭제 완료 후 Signal은 삭제된 설정을 조회할 수 없다"
    )
    void deleteAndSignalLookupAreSerialized() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        UUID autoTradingId =
                createAutoTrading(
                        userId,
                        strategyId
                );

        ExecutorService executorService =
                Executors.newFixedThreadPool(2);

        CountDownLatch deleteLockAcquired =
                new CountDownLatch(1);

        CountDownLatch allowDeleteCommit =
                new CountDownLatch(1);

        try {
            Future<Void> deleteFuture =
                    executorService.submit(() -> {
                        TransactionTemplate transactionTemplate =
                                new TransactionTemplate(
                                        transactionManager
                                );

                        transactionTemplate.executeWithoutResult(status -> {
                            AutoTrading autoTrading =
                                    autoTradingCommandRepository
                                            .findByIdAndUserIdForUpdate(
                                                    autoTradingId,
                                                    userId
                                            )
                                            .orElseThrow();

                            /*
                             * 삭제 트랜잭션이 AutoTrading row lock을
                             * 획득했음을 알린다.
                             */
                            deleteLockAcquired.countDown();

                            try {
                                /*
                                 * Signal 조회가 시작될 시간을 확보하기 위해
                                 * 삭제 Commit을 잠시 대기시킨다.
                                 */
                                allowDeleteCommit.await();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException(e);
                            }

                            autoTrading.softDelete(userId);
                        });

                        return null;
                    });

            Future<Boolean> signalFuture =
                    executorService.submit(() -> {
                        /*
                         * 삭제 Thread가 먼저 row lock을 획득하도록 한다.
                         */
                        deleteLockAcquired.await();

                        TransactionTemplate transactionTemplate =
                                new TransactionTemplate(
                                        transactionManager
                                );

                        return transactionTemplate.execute(status ->
                                autoTradingCommandRepository
                                        .findByUserIdAndStrategyIdForUpdate(
                                                userId,
                                                strategyId
                                        )
                                        .isPresent()
                        );
                    });

            /*
             * 삭제 트랜잭션이 soft delete 후 Commit하도록 진행시킨다.
             */
            allowDeleteCommit.countDown();

            deleteFuture.get();

            boolean signalFoundAutoTrading =
                    signalFuture.get();

            // then
            assertThat(signalFoundAutoTrading)
                    .isFalse();

            Integer activeRowCount =
                    jdbcTemplate.queryForObject(
                            """
                            SELECT COUNT(*)
                            FROM trading.p_auto_tradings
                            WHERE id = ?
                              AND deleted_at IS NULL
                            """,
                            Integer.class,
                            autoTradingId
                    );

            assertThat(activeRowCount)
                    .isZero();

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

    private UUID createAutoTrading(
            UUID userId,
            UUID strategyId
    ) {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(
                        transactionManager
                );

        return transactionTemplate.execute(status -> {
            AutoTrading autoTrading =
                    AutoTrading.create(
                            userId,
                            strategyId
                    );

            return autoTradingCommandRepository
                    .saveAndFlush(autoTrading)
                    .getId();
        });
    }
}