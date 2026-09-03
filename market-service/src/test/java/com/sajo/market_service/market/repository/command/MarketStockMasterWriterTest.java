package com.sajo.market_service.market.repository.command;

import com.sajo.market_service.market.dto.command.MarketStockMasterCommand;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MarketStockMasterWriterTest {

    @Test
    void usesPostgresUpsertWithoutOverwritingExistingIdentityOrCreationTime() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        MarketStockMasterWriter writer = new MarketStockMasterWriter(jdbcTemplate);
        given(jdbcTemplate.batchUpdate(anyString(), any(MapSqlParameterSource[].class))).willReturn(new int[]{1});

        writer.upsertAll(List.of(command()));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).batchUpdate(sql.capture(), any(MapSqlParameterSource[].class));
        String normalizedSql = sql.getValue().replaceAll("\\s+", " ").toLowerCase();
        assertThat(normalizedSql).contains("on conflict (stock_code) do update");
        assertThat(normalizedSql).doesNotContain("id = excluded.id");
        assertThat(normalizedSql).doesNotContain("created_at = excluded.created_at");
    }

    @Test
    void doesNotTreatSuccessNoInfoAsExactSavedCount() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        MarketStockMasterWriter writer = new MarketStockMasterWriter(jdbcTemplate);
        given(jdbcTemplate.batchUpdate(anyString(), any(MapSqlParameterSource[].class)))
                .willReturn(new int[]{1, Statement.SUCCESS_NO_INFO});

        assertThat(writer.upsertAll(List.of(command(), command()))).isEqualTo(1);
    }

    private MarketStockMasterCommand command() {
        return new MarketStockMasterCommand("005930", "삼성전자", "KOSPI", "001", 1_000_000L, null);
    }
}
