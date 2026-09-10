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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V105의 RAISE EXCEPTION 가드(non-null eps/bps 존재 시 컬럼 드롭 거부)가 실제로 동작하는지,
 * 그리고 모두 null인 정상 상태에서는 컬럼이 실제로 드롭되는지를 검증한다.
 * 이 가드가 지키는 건 복구 불가능한 과거 EPS/BPS 데이터라서, 조건이 깨지는 회귀(OR이 AND로
 * 바뀌는 등)를 코드 리뷰만으로 계속 잡아내기는 어렵다.
 */
@Testcontainers
class MarketStockIndicatorDropEpsBpsMigrationTest {

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
    void refusesToApplyWhenNonNullEpsRowExists() throws Exception {
        createLegacyTable();
        insertIndicatorRow(UUID.randomUUID(), "4605", null);

        assertThatThrownBy(this::applyMigration).isInstanceOf(SQLException.class);
        assertThat(columnExists("eps")).isTrue();
        assertThat(columnExists("bps")).isTrue();
    }

    @Test
    void refusesToApplyWhenNonNullBpsRowExists() throws Exception {
        createLegacyTable();
        insertIndicatorRow(UUID.randomUUID(), null, "51850");

        assertThatThrownBy(this::applyMigration).isInstanceOf(SQLException.class);
        assertThat(columnExists("eps")).isTrue();
        assertThat(columnExists("bps")).isTrue();
    }

    @Test
    void dropsColumnsWhenAllRowsHaveNullEpsAndBps() throws Exception {
        createLegacyTable();
        insertIndicatorRow(UUID.randomUUID(), null, null);

        applyMigration();

        assertThat(columnExists("eps")).isFalse();
        assertThat(columnExists("bps")).isFalse();
    }

    @Test
    void dropsColumnsWhenTableHasNoRowsAtAll() throws Exception {
        createLegacyTable();

        applyMigration();

        assertThat(columnExists("eps")).isFalse();
        assertThat(columnExists("bps")).isFalse();
    }

    private void createLegacyTable() throws SQLException {
        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA market_strategy");
            statement.execute("""
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
        }
    }

    private void insertIndicatorRow(UUID stockId, String eps, String bps) throws SQLException {
        String epsValue = eps == null ? "NULL" : eps;
        String bpsValue = bps == null ? "NULL" : bps;
        String sql = """
                INSERT INTO market_strategy.m_market_stocks_indicator
                    (id, stock_id, reference_date, eps, bps, created_at)
                VALUES ('%s', '%s', DATE '2026-09-01', %s, %s, CURRENT_TIMESTAMP)
                """.formatted(UUID.randomUUID(), stockId, epsValue, bpsValue);
        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private void applyMigration() throws IOException, SQLException {
        String sql;
        try (var inputStream = getClass()
                .getResourceAsStream("/db/migration/V105__market_stock_indicator_drop_eps_bps.sql")) {
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

    private boolean columnExists(String columnName) throws SQLException {
        String sql = "SELECT EXISTS (SELECT 1 FROM information_schema.columns "
                + "WHERE table_schema = 'market_strategy' AND table_name = 'm_market_stocks_indicator' "
                + "AND column_name = '" + columnName + "')";
        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getBoolean(1);
        }
    }
}
