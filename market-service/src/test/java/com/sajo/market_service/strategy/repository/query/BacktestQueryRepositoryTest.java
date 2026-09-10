package com.sajo.market_service.strategy.repository.query;

import com.sajo.common.config.CommonJpaAuditingAutoConfiguration;
import com.sajo.market_service.strategy.domain.Backtest;
import com.sajo.market_service.strategy.domain.Strategy;
import com.sajo.market_service.strategy.repository.command.BacktestCommandRepository;
import com.sajo.market_service.strategy.repository.command.StrategyCommandRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@Import(CommonJpaAuditingAutoConfiguration.class)
class BacktestQueryRepositoryTest {

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

    @Autowired
    private BacktestQueryRepository backtestQueryRepository;

    @Test
    @DisplayName("백테스트 ID, 전략 ID, 사용자 ID가 일치하면 조회된다")
    void findByIdAndStrategyIdAndUserIdAndDeletedAtIsNull() {
        // given
        UUID userId = UUID.randomUUID();
        Strategy strategy = strategyCommandRepository.saveAndFlush(newStrategy(userId));
        Backtest saved = backtestCommandRepository.saveAndFlush(newBacktest(strategy));

        // when
        Optional<Backtest> result = backtestQueryRepository.findByIdAndStrategyIdAndUserIdAndDeletedAtIsNull(
                saved.getId(),
                strategy.getId(),
                userId
        );

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(saved.getId());
        assertThat(result.get().getStrategyId()).isEqualTo(strategy.getId());
        assertThat(result.get().getUserId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("사용자 ID가 다르면 백테스트가 조회되지 않는다")
    void findByIdAndStrategyIdAndUserIdAndDeletedAtIsNullReturnsEmptyWhenUserIdMismatch() {
        // given
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Strategy strategy = strategyCommandRepository.saveAndFlush(newStrategy(userId));
        Backtest saved = backtestCommandRepository.saveAndFlush(newBacktest(strategy));

        // when
        Optional<Backtest> result = backtestQueryRepository.findByIdAndStrategyIdAndUserIdAndDeletedAtIsNull(
                saved.getId(),
                strategy.getId(),
                otherUserId
        );

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("소프트 삭제된 백테스트는 조회되지 않는다")
    void findByIdAndStrategyIdAndUserIdAndDeletedAtIsNullExcludesSoftDeleted() {
        // given
        UUID userId = UUID.randomUUID();
        Strategy strategy = strategyCommandRepository.saveAndFlush(newStrategy(userId));
        Backtest backtest = newBacktest(strategy);
        backtest.softDelete(userId);
        Backtest saved = backtestCommandRepository.saveAndFlush(backtest);

        // when
        Optional<Backtest> result = backtestQueryRepository.findByIdAndStrategyIdAndUserIdAndDeletedAtIsNull(
                saved.getId(),
                strategy.getId(),
                userId
        );

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("특정 전략의 본인 백테스트 목록만 조회된다")
    void findByStrategyIdAndUserIdAndDeletedAtIsNull() {
        // given
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Strategy strategy = strategyCommandRepository.saveAndFlush(newStrategy(userId));
        Strategy otherStrategy = strategyCommandRepository.saveAndFlush(newStrategy(otherUserId));

        backtestCommandRepository.saveAndFlush(newBacktest(strategy));
        backtestCommandRepository.saveAndFlush(newBacktest(otherStrategy));

        // when
        Page<Backtest> page = backtestQueryRepository.findByStrategyIdAndUserIdAndDeletedAtIsNull(
                strategy.getId(),
                userId,
                PageRequest.of(0, 10)
        );

        // then
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getStrategyId()).isEqualTo(strategy.getId());
        assertThat(page.getContent().get(0).getUserId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("내부 조회는 삭제되지 않은 백테스트만 조회한다")
    void findByIdAndDeletedAtIsNull() {
        // given
        UUID userId = UUID.randomUUID();
        Strategy strategy = strategyCommandRepository.saveAndFlush(newStrategy(userId));
        Backtest saved = backtestCommandRepository.saveAndFlush(newBacktest(strategy));

        // when
        Optional<Backtest> result = backtestQueryRepository.findByIdAndDeletedAtIsNull(saved.getId());

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(saved.getId());
    }

    private Backtest newBacktest(Strategy strategy) {
        return Backtest.request(
                strategy,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 31),
                1_000_000L
        );
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
