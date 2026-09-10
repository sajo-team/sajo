package com.sajo.market_service.strategy.repository;

import com.sajo.common.config.CommonJpaAuditingAutoConfiguration;
import com.sajo.market_service.strategy.domain.Backtest;
import com.sajo.market_service.strategy.domain.BacktestStatus;
import com.sajo.market_service.strategy.domain.Strategy;
import com.sajo.market_service.strategy.repository.command.BacktestCommandRepository;
import com.sajo.market_service.strategy.repository.command.StrategyCommandRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@Import(CommonJpaAuditingAutoConfiguration.class)
class BacktestCommandRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private StrategyCommandRepository strategyCommandRepository;

    @Autowired
    private BacktestCommandRepository backtestCommandRepository;

    @Test
    @DisplayName("백테스트 요청을 저장하면 REQUESTED 상태와 요청 시각이 유지된다")
    void saveBacktest() {
        // given
        UUID userId = UUID.randomUUID();
        Strategy strategy = strategyCommandRepository.saveAndFlush(newStrategy(userId));

        Backtest backtest = Backtest.request(
                strategy,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 31),
                1_000_000L
        );

        // when
        Backtest saved = backtestCommandRepository.saveAndFlush(backtest);

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStrategyId()).isEqualTo(strategy.getId());
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getStockCode()).isEqualTo("005930");
        assertThat(saved.getStartDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(saved.getEndDate()).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(saved.getInitialCash()).isEqualTo(1_000_000L);
        assertThat(saved.getStatus()).isEqualTo(BacktestStatus.REQUESTED);
        assertThat(saved.getRequestedAt()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    private Strategy newStrategy(UUID userId) {
        return Strategy.create(
                userId,
                UUID.randomUUID(),
                "005930",
                "삼성전자 눌림목 전략",
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
    }
}