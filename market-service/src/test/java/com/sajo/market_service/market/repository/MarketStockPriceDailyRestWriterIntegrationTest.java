package com.sajo.market_service.market.repository;

import com.sajo.market_service.market.dto.response.DailyPriceResponse;
import com.sajo.market_service.market.repository.command.MarketStockPriceDailyRestWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class MarketStockPriceDailyRestWriterIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private JdbcTemplate jdbcTemplate;
    private MarketStockPriceDailyRestWriter writer;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl() + "?currentSchema=market_strategy", postgres.getUsername(), postgres.getPassword());
        jdbcTemplate = new JdbcTemplate(dataSource);
        writer = new MarketStockPriceDailyRestWriter(new NamedParameterJdbcTemplate(dataSource));
        jdbcTemplate.execute("CREATE SCHEMA market_strategy");
        jdbcTemplate.execute("""
                CREATE TABLE market_strategy.m_market_stocks_price (
                    id UUID PRIMARY KEY,
                    stock_id UUID NOT NULL,
                    date DATE NOT NULL,
                    time TIME,
                    current_price BIGINT,
                    close_price BIGINT,
                    open_price BIGINT,
                    high_price BIGINT,
                    low_price BIGINT,
                    prev_close_price BIGINT,
                    change_price BIGINT,
                    change_rate NUMERIC,
                    volume BIGINT,
                    accumulated_volume BIGINT,
                    accumulated_trade_amount BIGINT,
                    foreign_ownership_rate NUMERIC,
                    source VARCHAR(20) NOT NULL,
                    created_at TIMESTAMP NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX uk_market_stock_price_daily_rest
                    ON market_strategy.m_market_stocks_price (stock_id, date)
                    WHERE time IS NULL AND source = 'REST'
                """);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS market_strategy CASCADE");
    }

    @Test
    void concurrentDuplicateRestInsertsKeepOneRowWithoutFailure() throws Exception {
        UUID stockId = UUID.randomUUID();
        DailyPriceResponse price = price(LocalDate.of(2026, 9, 1));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> insertAfterStart(stockId, price, ready, start));
            Future<Integer> second = executor.submit(() -> insertAfterStart(stockId, price, ready, start));
            assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(0, 1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM market_strategy.m_market_stocks_price", Integer.class)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void websocketRowsDoNotBlockRestDailyInsert() {
        UUID stockId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO market_strategy.m_market_stocks_price
                    (id, stock_id, date, time, current_price, source, created_at)
                VALUES (?, ?, ?, ?, ?, 'WEBSOCKET', CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), stockId, LocalDate.of(2026, 9, 1), java.sql.Time.valueOf("09:00:00"), 70_000L);
        jdbcTemplate.update("""
                INSERT INTO market_strategy.m_market_stocks_price
                    (id, stock_id, date, time, current_price, source, created_at)
                VALUES (?, ?, ?, ?, ?, 'WEBSOCKET', CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), stockId, LocalDate.of(2026, 9, 1), java.sql.Time.valueOf("09:01:00"), 70_001L);

        int inserted = writer.insertIgnoringDuplicates(stockId, List.of(price(LocalDate.of(2026, 9, 1))));

        assertThat(inserted).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM market_strategy.m_market_stocks_price
                WHERE source = 'WEBSOCKET'
                """, Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM market_strategy.m_market_stocks_price
                WHERE source = 'REST' AND time IS NULL
                """, Integer.class)).isEqualTo(1);
    }

    private int insertAfterStart(UUID stockId, DailyPriceResponse price, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(2, TimeUnit.SECONDS)) {
            throw new IllegalStateException("concurrent insert did not start");
        }
        return writer.insertIgnoringDuplicates(stockId, List.of(price));
    }

    private DailyPriceResponse price(LocalDate tradeDate) {
        return new DailyPriceResponse(tradeDate, 69_000L, 70_500L, 68_800L, 70_000L, 123_456L, 8_610_000_000L);
    }
}
