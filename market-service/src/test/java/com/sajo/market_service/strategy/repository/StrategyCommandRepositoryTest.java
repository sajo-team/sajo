package com.sajo.market_service.strategy.repository;

import com.sajo.common.config.CommonJpaAuditingAutoConfiguration;
import com.sajo.market_service.strategy.domain.Strategy;
import com.sajo.market_service.strategy.domain.StrategyStatus;
import com.sajo.market_service.strategy.repository.command.StrategyCommandRepository;
import com.sajo.market_service.strategy.repository.query.StrategyQueryRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@Import(CommonJpaAuditingAutoConfiguration.class)
class StrategyCommandRepositoryTest {

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16-alpine");
    @Autowired
    private StrategyQueryRepository strategyQueryRepository;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private StrategyCommandRepository strategyCommandRepository;

    @Test
    @DisplayName("전략을 저장하면 기본 상태는 INACTIVE이고 필드 값이 그대로 유지된다")
    void saveStrategy() {
        // given
        UUID userId = UUID.randomUUID();
        UUID stockId = UUID.randomUUID();

        Strategy strategy = Strategy.create(
                userId, stockId, "005930", "삼성전자 눌림목 전략",
                70_000L, 80_000L, new BigDecimal("5.0000"), null,
                3_000_000L, null, null, null
        );

        // when
        Strategy saved = strategyCommandRepository.saveAndFlush(strategy);

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getStockId()).isEqualTo(stockId);
        assertThat(saved.getStockCode()).isEqualTo("005930");
        assertThat(saved.getStatus()).isEqualTo(StrategyStatus.INACTIVE);
        assertThat(saved.getCreatedAt()).isNotNull(); // BaseUpdatableEntity auditing 확인
    }

    @Test
    @DisplayName("한 사용자가 여러 개의 전략을 저장할 수 있다")
    void saveMultipleStrategiesForSameUser() {
        // given
        UUID userId = UUID.randomUUID();

        Strategy first = Strategy.create(
                userId, UUID.randomUUID(), "005930", "전략 A",
                70_000L, 80_000L, new BigDecimal("5.0000"), null,
                3_000_000L, null, null, null
        );
        Strategy second = Strategy.create(
                userId, UUID.randomUUID(), "000660", "전략 B",
                100_000L, 120_000L, new BigDecimal("3.0000"), null,
                2_000_000L, null, null, null
        );

        // when
        strategyCommandRepository.saveAndFlush(first);
        strategyCommandRepository.saveAndFlush(second);

        // then — TradingLimit과 달리 사용자당 유니크 제약이 없으므로 둘 다 저장되어야 함
        assertThat(strategyCommandRepository.findAll()).hasSize(2);
    }
}
