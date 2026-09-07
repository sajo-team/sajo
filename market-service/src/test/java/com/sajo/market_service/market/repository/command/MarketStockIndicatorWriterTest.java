package com.sajo.market_service.market.repository.command;

import com.sajo.market_service.market.dto.command.MarketStockIndicatorCommand;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.math.BigDecimal;
import java.time.LocalDate;
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
                LocalDate.of(2026, 9, 4), new BigDecimal("15.2"), new BigDecimal("1.3"), null, null));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), any(SqlParameterSource.class));
        String normalizedSql = sql.getValue().replaceAll("\\s+", " ").toLowerCase();
        assertThat(normalizedSql).contains("on conflict (stock_id, reference_date) do update");
        assertThat(normalizedSql).contains(":bps, null, :createdat, :updatedat");
        assertThat(normalizedSql).doesNotContain("roe = excluded.roe");
        assertThat(normalizedSql).doesNotContain("roe = null");
        assertThat(normalizedSql).contains("updated_at = excluded.updated_at");
        assertThat(normalizedSql).contains("per = excluded.per");
        assertThat(normalizedSql).contains("pbr = excluded.pbr");
        assertThat(normalizedSql).contains("eps = excluded.eps");
        assertThat(normalizedSql).contains("bps = excluded.bps");
        assertThat(normalizedSql).doesNotContain("id = excluded.id");
        assertThat(normalizedSql).doesNotContain("created_at = excluded.created_at");
    }
}
