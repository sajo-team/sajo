package com.sajo.trading_service.ai_risk.integration;

import com.sajo.common.config.CommonJpaAuditingAutoConfiguration;
import com.sajo.trading_service.ai_risk.client.backtest.dto.BacktestInternalResponse;
import com.sajo.trading_service.ai_risk.client.strategy.dto.StrategyInternalResponse;
import com.sajo.trading_service.ai_risk.domain.AiAnalysisStatus;
import com.sajo.trading_service.ai_risk.domain.AiRiskAnalysis;
import com.sajo.trading_service.ai_risk.repository.command.AiRiskAnalysisCommandRepository;
import com.sajo.trading_service.ai_risk.service.command.AiRiskAnalysisPersistenceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@Tag("ai-risk")
@Testcontainers
@DataJpaTest
@Import({
        CommonJpaAuditingAutoConfiguration.class,
        AiRiskAnalysisPersistenceService.class
})
class AiRiskAnalysisConcurrencyTest {

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AiRiskAnalysisPersistenceService persistenceService;

    @Autowired
    private AiRiskAnalysisCommandRepository repository;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("동일 분석에 대한 동시 요청 시 PENDING 분석은 하나만 존재한다")
    void concurrentCreate_shouldKeepSinglePendingAnalysis()
            throws InterruptedException {

        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS uq_ai_risk_analysis_pending
                ON p_ai_risk_analyses (user_id, strategy_id, backtest_id)
                WHERE status = 'PENDING'
                """);

        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();
        UUID backtestId = UUID.randomUUID();

        StrategyInternalResponse strategy = new StrategyInternalResponse(
                strategyId,
                userId,
                "005930",
                "삼성전자 전략",
                70000L,
                80000L,
                new BigDecimal("5.0"),
                new BigDecimal("10.0"),
                1_000_000L,
                100_000L,
                new BigDecimal("10.0"),
                new BigDecimal("1.0"),
                new BigDecimal("10.0"),
                "ACTIVE"
        );

        BacktestInternalResponse backtest = new BacktestInternalResponse(
                backtestId,
                strategyId,
                userId,
                "005930",
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31),
                10_000_000L,
                "COMPLETED",
                new BigDecimal("12.5"),
                new BigDecimal("8.3"),
                new BigDecimal("60.0"),
                20,
                3
        );

        int threadCount = 2;

        ExecutorService executorService =
                Executors.newFixedThreadPool(threadCount);

        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        List<Throwable> exceptions = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    ready.countDown();
                    start.await();

                    persistenceService.create(
                            userId,
                            strategyId,
                            backtestId,
                            strategy,
                            backtest
                    );

                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await();

        executorService.shutdown();

        List<AiRiskAnalysis> analyses = repository.findAll();

        long pendingCount = analyses.stream()
                .filter(analysis ->
                        analysis.getStatus() == AiAnalysisStatus.PENDING)
                .count();

        assertThat(pendingCount).isEqualTo(1);
        assertThat(analyses).hasSize(1);

        assertThat(exceptions).hasSize(1);
        assertThat(exceptions.getFirst()).isInstanceOf(DataIntegrityViolationException.class);
    }
}