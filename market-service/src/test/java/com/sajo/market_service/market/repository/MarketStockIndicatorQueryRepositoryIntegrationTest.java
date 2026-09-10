package com.sajo.market_service.market.repository;

import com.sajo.common.config.CommonJpaAuditingAutoConfiguration;
import com.sajo.market_service.market.domain.MarketStockIndicator;
import com.sajo.market_service.market.repository.query.MarketStockIndicatorQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * findBy...(stockId, Pageable) 형태의 Derived Query 이름이 실제로 Spring Data가
 * 파싱 가능한 유효한 쿼리로 이어지는지, 정렬·필터가 의도대로 동작하는지 실제 DB로 검증한다.
 * 서비스 레벨 단위 테스트는 이 Repository를 mock 처리하므로 이 검증을 대신할 수 없다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(CommonJpaAuditingAutoConfiguration.class)
@Testcontainers
class MarketStockIndicatorQueryRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withUrlParam("currentSchema", "market_strategy");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MarketStockIndicatorQueryRepository repository;

    private final UUID stockId = UUID.randomUUID();

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "market_strategy");

        try (var connection = postgres.createConnection(""); var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS market_strategy");
        } catch (Exception exception) {
            throw new IllegalStateException("Testcontainers schema setup failed", exception);
        }
    }

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.update("DELETE FROM market_strategy.m_market_stocks_indicator");
        jdbcTemplate.update("DELETE FROM market_strategy.m_market_stocks");
        insertStock(stockId, "005930");
    }

    @Test
    void returnsFinancialHistoryOrderedByReferenceYearMonthDescAndExcludesLegacyRows() {
        insertFinancialRow(stockId, "2026-03", "2026-05-15T01:00:00Z");
        insertFinancialRow(stockId, "2026-09", "2026-09-10T01:00:00Z");
        insertFinancialRow(stockId, "2026-06", "2026-08-10T01:00:00Z");
        insertLegacyRow(stockId, LocalDate.of(2026, 9, 9));

        List<MarketStockIndicator> history = repository
                .findByStockIdAndFinancialPeriodTypeIsNotNullAndFinancialReferenceYearMonthIsNotNullOrderByFinancialReferenceYearMonthDescFinancialFetchedAtDesc(
                        stockId, PageRequest.of(0, 40));

        assertThat(history).extracting(MarketStockIndicator::getFinancialReferenceYearMonth)
                .containsExactly("2026-09", "2026-06", "2026-03");
        assertThat(history).allSatisfy(indicator -> assertThat(indicator.getReferenceDate()).isNull());
    }

    @Test
    void limitsFinancialHistoryByPageable() {
        insertFinancialRow(stockId, "2026-03", "2026-03-10T01:00:00Z");
        insertFinancialRow(stockId, "2026-06", "2026-06-10T01:00:00Z");
        insertFinancialRow(stockId, "2026-09", "2026-09-10T01:00:00Z");

        List<MarketStockIndicator> history = repository
                .findByStockIdAndFinancialPeriodTypeIsNotNullAndFinancialReferenceYearMonthIsNotNullOrderByFinancialReferenceYearMonthDescFinancialFetchedAtDesc(
                        stockId, PageRequest.of(0, 2));

        assertThat(history).extracting(MarketStockIndicator::getFinancialReferenceYearMonth)
                .containsExactly("2026-09", "2026-06");
    }

    @Test
    void returnsLegacyHistoryOrderedByReferenceDateDescAndExcludesFinancialRows() {
        insertLegacyRow(stockId, LocalDate.of(2026, 7, 1));
        insertLegacyRow(stockId, LocalDate.of(2026, 9, 1));
        insertLegacyRow(stockId, LocalDate.of(2026, 8, 1));
        insertFinancialRow(stockId, "2026-09", "2026-09-10T01:00:00Z");

        List<MarketStockIndicator> history = repository
                .findByStockIdAndReferenceDateIsNotNullOrderByReferenceDateDescCreatedAtDesc(
                        stockId, PageRequest.of(0, 40));

        assertThat(history).extracting(MarketStockIndicator::getReferenceDate)
                .containsExactly(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 7, 1));
        assertThat(history).allSatisfy(indicator -> assertThat(indicator.getFinancialReferenceYearMonth()).isNull());
    }

    @Test
    void returnsEmptyListsWhenStockHasNoIndicatorRows() {
        assertThat(repository
                .findByStockIdAndFinancialPeriodTypeIsNotNullAndFinancialReferenceYearMonthIsNotNullOrderByFinancialReferenceYearMonthDescFinancialFetchedAtDesc(
                        stockId, PageRequest.of(0, 40)))
                .isEmpty();
        assertThat(repository
                .findByStockIdAndReferenceDateIsNotNullOrderByReferenceDateDescCreatedAtDesc(
                        stockId, PageRequest.of(0, 40)))
                .isEmpty();
    }

    private void insertStock(UUID id, String stockCode) {
        jdbcTemplate.update("""
                INSERT INTO market_strategy.m_market_stocks
                    (id, stock_code, stock_name, market_type, created_at)
                VALUES (?, ?, ?, 'KOSPI', CURRENT_TIMESTAMP)
                """, id, stockCode, "test-" + stockCode);
    }

    private void insertLegacyRow(UUID stockId, LocalDate referenceDate) {
        jdbcTemplate.update("""
                INSERT INTO market_strategy.m_market_stocks_indicator (id, stock_id, reference_date, created_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), stockId, referenceDate);
    }

    private void insertFinancialRow(UUID stockId, String yearMonth, String fetchedAtIso) {
        jdbcTemplate.update("""
                INSERT INTO market_strategy.m_market_stocks_indicator
                    (id, stock_id, reference_date, financial_period_type, financial_reference_year_month,
                     financial_fetched_at, created_at)
                VALUES (?, ?, null, 'QUARTER', ?, ?::timestamptz, CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), stockId, yearMonth, fetchedAtIso);
    }
}
