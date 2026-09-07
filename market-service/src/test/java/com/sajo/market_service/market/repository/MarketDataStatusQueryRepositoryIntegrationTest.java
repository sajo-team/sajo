package com.sajo.market_service.market.repository;

import com.sajo.common.config.CommonJpaAuditingAutoConfiguration;
import com.sajo.market_service.market.repository.query.MarketDataStatusProjection;
import com.sajo.market_service.market.repository.query.MarketDataStatusQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(CommonJpaAuditingAutoConfiguration.class)
@Testcontainers
class MarketDataStatusQueryRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withUrlParam("currentSchema", "market_strategy");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MarketDataStatusQueryRepository repository;

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
        jdbcTemplate.update("DELETE FROM market_strategy.m_market_stocks_price");
        jdbcTemplate.update("DELETE FROM market_strategy.m_market_stocks_indicator");
        jdbcTemplate.update("DELETE FROM market_strategy.m_market_stocks");
    }

    @Test
    void aggregatesOnlyValidRestDailyRowsAndMapsPostgresTypes() {
        UUID firstStock = UUID.randomUUID();
        UUID secondStock = UUID.randomUUID();
        insertStock(firstStock, "005930");
        insertStock(secondStock, "000660");

        // Included: two dates for one stock and one valid row for another stock.
        insertPrice(firstStock, "2026-09-05", null, 70_000L, "REST");
        insertPrice(firstStock, "2026-09-07", null, 71_000L, "REST");
        insertPrice(secondStock, "2026-09-06", null, 150_000L, "REST");

        // Excluded: websocket, intraday REST, and REST daily row without close price.
        insertPrice(firstStock, "2026-09-08", "09:00:00", 71_500L, "WEBSOCKET");
        insertPrice(firstStock, "2026-09-09", "09:01:00", 71_600L, "REST");
        insertPrice(secondStock, "2026-09-10", null, null, "REST");

        insertIndicator(firstStock, "2026-09-01");
        insertIndicator(firstStock, "2026-09-07");
        insertIndicator(secondStock, "2026-09-06");

        MarketDataStatusProjection status = repository.findStatus();

        assertThat(status.getTotalStockCount()).isEqualTo(2L);
        assertThat(status.getDailyPriceStockCount()).isEqualTo(2L);
        assertThat(status.getLatestDailyPriceDate()).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(status.getIndicatorStockCount()).isEqualTo(2L);
        assertThat(status.getLatestIndicatorReferenceDate()).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(status.getDailyPriceStockCount()).isInstanceOf(Long.class);
        assertThat(status.getLatestDailyPriceDate()).isInstanceOf(LocalDate.class);
        assertThat(status.getLatestIndicatorReferenceDate()).isInstanceOf(LocalDate.class);
    }

    @Test
    void returnsZeroCountsAndNullDatesWhenTablesAreEmpty() {
        MarketDataStatusProjection status = repository.findStatus();

        assertThat(status.getTotalStockCount()).isEqualTo(0L);
        assertThat(status.getDailyPriceStockCount()).isEqualTo(0L);
        assertThat(status.getLatestDailyPriceDate()).isNull();
        assertThat(status.getIndicatorStockCount()).isEqualTo(0L);
        assertThat(status.getLatestIndicatorReferenceDate()).isNull();
    }

    private void insertStock(UUID id, String stockCode) {
        jdbcTemplate.update("""
                INSERT INTO market_strategy.m_market_stocks
                    (id, stock_code, stock_name, market_type, created_at)
                VALUES (?, ?, ?, 'KOSPI', CURRENT_TIMESTAMP)
                """, id, stockCode, "test-" + stockCode);
    }

    private void insertPrice(UUID stockId, String date, String time, Long closePrice, String source) {
        jdbcTemplate.update("""
                INSERT INTO market_strategy.m_market_stocks_price
                    (id, stock_id, date, time, close_price, source, created_at)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), stockId, LocalDate.parse(date), time, closePrice, source);
    }

    private void insertIndicator(UUID stockId, String referenceDate) {
        jdbcTemplate.update("""
                INSERT INTO market_strategy.m_market_stocks_indicator (id, stock_id, reference_date, created_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), stockId, LocalDate.parse(referenceDate));
    }
}
