package com.sajo.market_service.market.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class MarketStockPriceDailyMigrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @AfterEach
    void cleanUpSchema() throws SQLException {
        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS market_strategy CASCADE");
        }
    }

    @Test
    void appliesMigrationCleansRestDuplicatesAndCreatesPartialUniqueIndex() throws Exception {
        createLegacyTable(false);
        UUID stockId = UUID.randomUUID();
        UUID retainedRestId = UUID.randomUUID();
        UUID duplicatedRestId = UUID.randomUUID();
        LocalDateTime firstCreatedAt = LocalDateTime.of(2026, 9, 1, 9, 0);

        insertPrice(retainedRestId, stockId, "2026-09-01", null, "REST", firstCreatedAt);
        insertPrice(duplicatedRestId, stockId, "2026-09-01", null, "REST", firstCreatedAt.plusMinutes(1));
        insertPrice(UUID.randomUUID(), stockId, "2026-09-02", "09:00:00", "WEBSOCKET", firstCreatedAt);
        insertPrice(UUID.randomUUID(), stockId, "2026-09-02", "09:01:00", "WEBSOCKET", firstCreatedAt.plusMinutes(1));

        applyMigration();

        assertThat(countRows("WHERE stock_id = '%s' AND date = DATE '2026-09-01' AND time IS NULL AND source = 'REST'"
                .formatted(stockId))).isEqualTo(1);
        assertThat(singleUuid("SELECT id FROM market_strategy.m_market_stocks_price "
                + "WHERE stock_id = '%s' AND date = DATE '2026-09-01' AND time IS NULL AND source = 'REST'"
                .formatted(stockId))).isEqualTo(retainedRestId);
        assertThat(countRows("WHERE stock_id = '%s' AND date = DATE '2026-09-02' AND source = 'WEBSOCKET'"
                .formatted(stockId))).isEqualTo(2);
        assertThat(indexExists("uk_market_stock_price_daily_rest")).isTrue();

        assertThatThrownBy(() -> insertPrice(UUID.randomUUID(), stockId, "2026-09-01", null, "REST", firstCreatedAt))
                .isInstanceOf(SQLException.class);

        // Partial index applies only to REST daily rows; existing WEBSOCKET rows do not block a REST daily row.
        insertPrice(UUID.randomUUID(), stockId, "2026-09-02", null, "REST", firstCreatedAt);
        assertThat(countRows("WHERE stock_id = '%s' AND date = DATE '2026-09-02' AND time IS NULL AND source = 'REST'"
                .formatted(stockId))).isEqualTo(1);
    }

    @Test
    void removesLegacyUniqueConstraintsAndAppliesExpectedColumnChanges() throws Exception {
        createLegacyTable(true);

        applyMigration();
        applyMigration();

        assertThat(constraintExists("uk_market_stock_price_stock_date_time_source")).isFalse();
        assertThat(constraintExists("uk_market_stock_price_stock_date")).isFalse();
        assertThat(columnExists("close_price")).isTrue();
        assertThat(columnExists("accumulated_trade_amount")).isTrue();
        assertThat(columnIsNullable("current_price")).isTrue();
    }

    private void createLegacyTable(boolean withLegacyConstraints) throws SQLException {
        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA market_strategy");
            statement.execute("""
                    CREATE TABLE market_strategy.m_market_stocks_price (
                        id UUID PRIMARY KEY,
                        stock_id UUID NOT NULL,
                        date DATE NOT NULL,
                        time TIME,
                        current_price BIGINT NOT NULL,
                        trade_amount BIGINT,
                        source VARCHAR(20) NOT NULL,
                        created_at TIMESTAMP
                    )
                    """);
            if (withLegacyConstraints) {
                statement.execute("ALTER TABLE market_strategy.m_market_stocks_price "
                        + "ADD CONSTRAINT uk_market_stock_price_stock_date_time_source "
                        + "UNIQUE (stock_id, date, time, source)");
                statement.execute("ALTER TABLE market_strategy.m_market_stocks_price "
                        + "ADD CONSTRAINT uk_market_stock_price_stock_date UNIQUE (stock_id, date)");
            }
        }
    }

    private void applyMigration() throws IOException, SQLException {
        String sql;
        try (var inputStream = getClass().getResourceAsStream("/db/migration/V44__market_stock_price_daily_unique.sql")) {
            if (inputStream == null) {
                throw new IllegalStateException("Migration SQL resource was not found");
            }
            sql = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void insertPrice(UUID id, UUID stockId, String date, String time, String source, LocalDateTime createdAt)
            throws SQLException {
        String timeValue = time == null ? "NULL" : "TIME '%s'".formatted(time);
        String sql = """
                INSERT INTO market_strategy.m_market_stocks_price
                    (id, stock_id, date, time, current_price, accumulated_trade_amount, source, created_at)
                VALUES ('%s', '%s', DATE '%s', %s, 70000, 8610000000, '%s', TIMESTAMP '%s')
                """.formatted(id, stockId, date, timeValue, source, createdAt);
        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private int countRows(String whereClause) throws SQLException {
        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) FROM market_strategy.m_market_stocks_price " + whereClause)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private UUID singleUuid(String sql) throws SQLException {
        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getObject(1, UUID.class);
        }
    }

    private boolean indexExists(String indexName) throws SQLException {
        String sql = "SELECT EXISTS (SELECT 1 FROM pg_indexes "
                + "WHERE schemaname = 'market_strategy' AND indexname = '" + indexName + "')";
        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getBoolean(1);
        }
    }

    private boolean constraintExists(String constraintName) throws SQLException {
        return databaseObjectExists("information_schema.table_constraints", "constraint_name", constraintName);
    }

    private boolean databaseObjectExists(String table, String nameColumn, String name) throws SQLException {
        String sql = "SELECT EXISTS (SELECT 1 FROM " + table
                + " WHERE " + nameColumn + " = '" + name + "' AND table_schema = 'market_strategy')";
        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getBoolean(1);
        }
    }

    private boolean columnExists(String columnName) throws SQLException {
        String sql = "SELECT EXISTS (SELECT 1 FROM information_schema.columns "
                + "WHERE table_schema = 'market_strategy' AND table_name = 'm_market_stocks_price' "
                + "AND column_name = '" + columnName + "')";
        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getBoolean(1);
        }
    }

    private boolean columnIsNullable(String columnName) throws SQLException {
        String sql = "SELECT is_nullable FROM information_schema.columns "
                + "WHERE table_schema = 'market_strategy' AND table_name = 'm_market_stocks_price' "
                + "AND column_name = '" + columnName + "'";
        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return "YES".equals(resultSet.getString(1));
        }
    }
}
