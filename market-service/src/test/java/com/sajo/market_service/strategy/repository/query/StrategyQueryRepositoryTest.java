package com.sajo.market_service.strategy.repository.query;

import com.sajo.common.config.CommonJpaAuditingAutoConfiguration;
import com.sajo.market_service.strategy.domain.Strategy;
import com.sajo.market_service.strategy.domain.StrategyStatus;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@Import(CommonJpaAuditingAutoConfiguration.class)
class StrategyQueryRepositoryTest {

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
    private StrategyQueryRepository strategyQueryRepository;

    private Strategy newStrategy(UUID userId, String stockCode, String name) {
        return Strategy.create(
                userId, UUID.randomUUID(), stockCode, name,
                70_000L, 80_000L, new BigDecimal("5.0000"), null,
                1_000_000L, null, null, null
        );
    }

    @Test
    @DisplayName("본인이 생성한 전략만 조회된다")
    void findStrategiesOnlyReturnsOwnStrategies() {
        // given
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        strategyQueryRepository.saveAndFlush(newStrategy(userId, "005930", "내 전략"));
        strategyQueryRepository.saveAndFlush(newStrategy(otherUserId, "000660", "다른 사람 전략"));

        // when
        Page<Strategy> page = strategyQueryRepository.findStrategies(
                userId, null, null, PageRequest.of(0, 10)
        );

        // then
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getUserId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("status 조건으로 필터링할 수 있다")
    void findStrategiesFiltersByStatus() {
        // given
        UUID userId = UUID.randomUUID();
        strategyQueryRepository.saveAndFlush(newStrategy(userId, "005930", "전략 A"));

        // when
        Page<Strategy> activeOnly = strategyQueryRepository.findStrategies(
                userId, StrategyStatus.ACTIVE, null, PageRequest.of(0, 10)
        );
        Page<Strategy> inactiveOnly = strategyQueryRepository.findStrategies(
                userId, StrategyStatus.INACTIVE, null, PageRequest.of(0, 10)
        );

        // then — 생성 직후 상태는 INACTIVE이므로
        assertThat(activeOnly.getContent()).isEmpty();
        assertThat(inactiveOnly.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("stockCode 조건으로 필터링할 수 있다")
    void findStrategiesFiltersByStockCode() {
        // given
        UUID userId = UUID.randomUUID();
        strategyQueryRepository.saveAndFlush(newStrategy(userId, "005930", "삼성전자 전략"));
        strategyQueryRepository.saveAndFlush(newStrategy(userId, "000660", "SK하이닉스 전략"));

        // when
        Page<Strategy> page = strategyQueryRepository.findStrategies(
                userId, null, "005930", PageRequest.of(0, 10)
        );

        // then
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getStockCode()).isEqualTo("005930");
    }

    @Test
    @DisplayName("소프트 삭제된 전략은 목록에서 제외된다")
    void findStrategiesExcludesSoftDeleted() {
        // given
        UUID userId = UUID.randomUUID();
        Strategy strategy = newStrategy(userId, "005930", "삭제될 전략");
        strategy.softDelete(userId);
        strategyQueryRepository.saveAndFlush(strategy);

        // when
        Page<Strategy> page = strategyQueryRepository.findStrategies(
                userId, null, null, PageRequest.of(0, 10)
        );

        // then
        assertThat(page.getContent()).isEmpty();
    }

    @Test
    @DisplayName("페이징이 적용된다")
    void findStrategiesAppliesPaging() {
        // given
        UUID userId = UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            strategyQueryRepository.saveAndFlush(newStrategy(userId, "005930", "전략 " + i));
        }

        // when
        Page<Strategy> page = strategyQueryRepository.findStrategies(
                userId, null, null, PageRequest.of(0, 2)
        );

        // then
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }
}
