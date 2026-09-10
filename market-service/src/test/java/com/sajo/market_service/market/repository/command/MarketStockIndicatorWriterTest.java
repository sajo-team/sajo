package com.sajo.market_service.market.repository.command;

import com.sajo.market_service.market.dto.command.MarketStockIndicatorCommand;
import com.sajo.market_service.market.domain.FinancialPeriodType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MarketStockIndicatorWriterTest {

    @Test
    void upsertsByStockAndKisMarketDateWithoutOverwritingIdentityOrCreationTime() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        MarketStockIndicatorWriter writer = new MarketStockIndicatorWriter(jdbcTemplate);

        writer.upsert(UUID.randomUUID(), new MarketStockIndicatorCommand(
                new BigDecimal("15.2"), new BigDecimal("1.3"),
                Instant.parse("2026-09-10T01:00:00Z"), new BigDecimal("8.7"),
                FinancialPeriodType.QUARTER, YearMonth.of(2026, 6), Instant.parse("2026-09-10T01:00:01Z")));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), any(SqlParameterSource.class));
        String normalizedSql = sql.getValue().replaceAll("\\s+", " ").toLowerCase();
        assertThat(normalizedSql).contains("on conflict (stock_id, financial_period_type, financial_reference_year_month)");
        assertThat(normalizedSql).contains("roe = coalesce(excluded.roe, m_market_stocks_indicator.roe)");
        assertThat(normalizedSql).contains("updated_at = excluded.updated_at");
        assertThat(normalizedSql).contains("per = coalesce(excluded.per, m_market_stocks_indicator.per)");
        assertThat(normalizedSql).contains("pbr = coalesce(excluded.pbr, m_market_stocks_indicator.pbr)");
        assertThat(normalizedSql).doesNotContain("eps = coalesce(excluded.eps");
        assertThat(normalizedSql).doesNotContain("bps = coalesce(excluded.bps");
        assertThat(normalizedSql).doesNotContain("id = excluded.id");
        assertThat(normalizedSql).doesNotContain("created_at = excluded.created_at");
    }
}
