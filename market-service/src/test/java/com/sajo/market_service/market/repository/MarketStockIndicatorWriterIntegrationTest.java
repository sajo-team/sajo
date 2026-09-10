package com.sajo.market_service.market.repository;

import com.sajo.market_service.market.dto.command.MarketStockIndicatorCommand;
import com.sajo.market_service.market.domain.FinancialPeriodType;
import com.sajo.market_service.market.repository.command.MarketStockIndicatorWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class MarketStockIndicatorWriterIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withUrlParam("currentSchema", "market_strategy");

    private JdbcTemplate jdbcTemplate;
    private MarketStockIndicatorWriter writer;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbcTemplate = new JdbcTemplate(dataSource);
        writer = new MarketStockIndicatorWriter(new NamedParameterJdbcTemplate(dataSource));
        jdbcTemplate.execute("CREATE SCHEMA market_strategy");
        jdbcTemplate.execute("""
                CREATE TABLE market_strategy.m_market_stocks_indicator (
                    id UUID PRIMARY KEY,
                    stock_id UUID NOT NULL,
                    reference_date DATE,
                    per NUMERIC(10, 4),
                    pbr NUMERIC(10, 4),
                    eps NUMERIC(15, 2),
                    bps NUMERIC(15, 2),
                    roe NUMERIC(10, 4),
                    valuation_fetched_at TIMESTAMP WITH TIME ZONE,
                    financial_period_type VARCHAR(20),
                    financial_reference_year_month VARCHAR(7),
                    financial_fetched_at TIMESTAMP WITH TIME ZONE,
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                    updated_at TIMESTAMP WITH TIME ZONE
                )
                """);
        // This is the V104 partial unique key used by the writer's ON CONFLICT target.
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX uk_market_stock_indicator_financial_period
                    ON market_strategy.m_market_stocks_indicator
                        (stock_id, financial_period_type, financial_reference_year_month)
                    WHERE financial_period_type IS NOT NULL
                      AND financial_reference_year_month IS NOT NULL
                """);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS market_strategy CASCADE");
    }

    @Test
    void upsertsSameSnapshotWithoutGrowingRowsAndPreservesExistingFields() {
        UUID stockId = UUID.randomUUID();
        LocalDate referenceDate = LocalDate.of(2026, 9, 4);
        MarketStockIndicatorCommand first = command(referenceDate, "15.2", "1.3");
        writer.upsert(stockId, first);

        UUID originalId = jdbcTemplate.queryForObject(
                "SELECT id FROM market_strategy.m_market_stocks_indicator WHERE stock_id = ?",
                (resultSet, rowNum) -> resultSet.getObject(1, UUID.class), stockId);
        OffsetDateTime originalCreatedAt = OffsetDateTime.of(2026, 9, 4, 8, 0, 0, 0, ZoneOffset.UTC);
        jdbcTemplate.update("""
                UPDATE market_strategy.m_market_stocks_indicator
                SET roe = 8.7, eps = 4605, bps = 51850, created_at = ?, updated_at = ?
                WHERE id = ?
                """, originalCreatedAt, originalCreatedAt, originalId);

        writer.upsert(stockId, command(referenceDate, "16.2", "1.4"));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM market_strategy.m_market_stocks_indicator", Integer.class))
                .isEqualTo(1);
        UUID reloadedId = jdbcTemplate.queryForObject(
                "SELECT id FROM market_strategy.m_market_stocks_indicator WHERE stock_id = ?",
                (resultSet, rowNum) -> resultSet.getObject(1, UUID.class), stockId);
        assertThat(reloadedId).isEqualTo(originalId);
        assertThat(jdbcTemplate.queryForObject("SELECT created_at FROM market_strategy.m_market_stocks_indicator WHERE id = ?",
                OffsetDateTime.class, originalId)).isEqualTo(originalCreatedAt);
        assertThat(jdbcTemplate.queryForObject("SELECT roe FROM market_strategy.m_market_stocks_indicator WHERE id = ?",
                BigDecimal.class, originalId)).isEqualByComparingTo("8.7");
        assertMetrics(originalId, "16.2", "1.4", "4605", "51850");
        assertThat(jdbcTemplate.queryForObject("SELECT updated_at FROM market_strategy.m_market_stocks_indicator WHERE id = ?",
                OffsetDateTime.class, originalId)).isAfter(originalCreatedAt);

        OffsetDateTime valuationFetchedAt = jdbcTemplate.queryForObject(
                "SELECT valuation_fetched_at FROM market_strategy.m_market_stocks_indicator WHERE id = ?",
                OffsetDateTime.class, originalId);
        writer.upsert(stockId, command(referenceDate, "17.2", null, Instant.parse("2026-09-10T02:00:00Z")));

        assertMetrics(originalId, "16.2", "1.4", "4605", "51850");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT valuation_fetched_at FROM market_strategy.m_market_stocks_indicator WHERE id = ?",
                OffsetDateTime.class, originalId)).isEqualTo(valuationFetchedAt);
        assertThat(jdbcTemplate.queryForObject("SELECT roe FROM market_strategy.m_market_stocks_indicator WHERE id = ?",
                BigDecimal.class, originalId)).isEqualByComparingTo("8.7");
    }

    @Test
    void storesAnotherReferenceDateAsAnotherRow() {
        UUID stockId = UUID.randomUUID();
        writer.upsert(stockId, command(LocalDate.of(2026, 9, 4), "15.2", "1.3"));
        writer.upsert(stockId, command(LocalDate.of(2026, 10, 5), "15.3", "1.4"));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM market_strategy.m_market_stocks_indicator WHERE stock_id = ?",
                Integer.class, stockId)).isEqualTo(2);
    }

    @Test
    void v104UniqueKeyMatchesUpsertConflictTarget() {
        UUID stockId = UUID.randomUUID();
        writer.upsert(stockId, command(LocalDate.of(2026, 9, 4), "15.2", "1.3"));

        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE schemaname = 'market_strategy'
                  AND tablename = 'm_market_stocks_indicator'
                  AND indexname = 'uk_market_stock_indicator_financial_period'
                  AND indexdef LIKE '%(stock_id, financial_period_type, financial_reference_year_month)%'
                """, Integer.class)).isEqualTo(1);
    }

    private void assertMetrics(UUID id, String per, String pbr, String eps, String bps) {
        var values = jdbcTemplate.queryForMap("SELECT per, pbr, eps, bps FROM market_strategy.m_market_stocks_indicator WHERE id = ?", id);
        assertThat((BigDecimal) values.get("per")).isEqualByComparingTo(per);
        assertThat((BigDecimal) values.get("pbr")).isEqualByComparingTo(pbr);
        assertThat((BigDecimal) values.get("eps")).isEqualByComparingTo(eps);
        assertThat((BigDecimal) values.get("bps")).isEqualByComparingTo(bps);
    }

    private MarketStockIndicatorCommand command(LocalDate date, String per, String pbr) {
        return command(date, per, pbr, Instant.parse("2026-09-10T01:00:00Z"));
    }

    private MarketStockIndicatorCommand command(LocalDate date, String per, String pbr, Instant valuationFetchedAt) {
        return new MarketStockIndicatorCommand(decimal(per), decimal(pbr),
                valuationFetchedAt, new BigDecimal("8.7"),
                FinancialPeriodType.QUARTER, YearMonth.from(date), Instant.parse("2026-09-10T01:00:01Z"));
    }

    private BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
