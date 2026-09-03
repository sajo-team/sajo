package com.sajo.trading_service.trading.repository.command;

import com.sajo.common.config.CommonJpaAuditingAutoConfiguration;
import com.sajo.trading_service.trading.domain.TradingLimit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DataJpaTest
@Import(CommonJpaAuditingAutoConfiguration.class)
class TradingLimitCommandRepositoryTest {

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
    private TradingLimitCommandRepository tradingLimitCommandRepository;

    @Test
    @DisplayName("거래 한도를 저장할 수 있다")
    void saveTradingLimit() {
        // given
        UUID userId = UUID.randomUUID();

        TradingLimit tradingLimit = TradingLimit.create(
                userId,
                3_000_000L,
                10,
                new BigDecimal("5.00")
        );

        // when
        TradingLimit saved =
                tradingLimitCommandRepository.saveAndFlush(tradingLimit);

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getDailyMaxOrderAmount()).isEqualTo(3_000_000L);
        assertThat(saved.getDailyMaxOrderCount()).isEqualTo(10);
        assertThat(saved.getDailyLossLimitRate())
                .isEqualByComparingTo("5.00");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("같은 사용자의 거래 한도를 두 번 저장할 수 없다")
    void duplicateUserTradingLimit() {
        // given
        UUID userId = UUID.randomUUID();

        TradingLimit first = TradingLimit.create(
                userId,
                3_000_000L,
                10,
                new BigDecimal("5.00")
        );

        TradingLimit second = TradingLimit.create(
                userId,
                5_000_000L,
                20,
                new BigDecimal("10.00")
        );

        tradingLimitCommandRepository.saveAndFlush(first);

        // when & then
        assertThatThrownBy(() ->
                tradingLimitCommandRepository.saveAndFlush(second)
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("거래 한도를 비관적 락으로 조회할 수 있다")
    void findByUserIdForUpdate() {
        // given
        UUID userId = UUID.randomUUID();

        TradingLimit tradingLimit = TradingLimit.create(
                userId,
                3_000_000L,
                10,
                new BigDecimal("5.00")
        );

        tradingLimitCommandRepository.saveAndFlush(tradingLimit);

        // when
        TradingLimit found = tradingLimitCommandRepository
                .findByUserIdForUpdate(userId)
                .orElseThrow();

        // then
        assertThat(found.getUserId()).isEqualTo(userId);
        assertThat(found.getDailyMaxOrderAmount()).isEqualTo(3_000_000L);
        assertThat(found.getDailyMaxOrderCount()).isEqualTo(10);
    }
}