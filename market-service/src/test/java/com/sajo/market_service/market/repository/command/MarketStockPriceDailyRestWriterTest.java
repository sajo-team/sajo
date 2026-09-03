package com.sajo.market_service.market.repository.command;

import com.sajo.market_service.market.dto.response.DailyPriceResponse;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class MarketStockPriceDailyRestWriterTest {

    @Test
    void excludesSuccessNoInfoFromKnownInsertedCount() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        MarketStockPriceDailyRestWriter writer = new MarketStockPriceDailyRestWriter(jdbcTemplate);
        given(jdbcTemplate.batchUpdate(anyString(), any(MapSqlParameterSource[].class)))
                .willReturn(new int[]{1, Statement.SUCCESS_NO_INFO, 0});

        int inserted = writer.insertIgnoringDuplicates(UUID.randomUUID(), List.of(
                price(LocalDate.of(2026, 9, 1)),
                price(LocalDate.of(2026, 9, 2)),
                price(LocalDate.of(2026, 9, 3))
        ));

        assertThat(inserted).isEqualTo(1);
    }

    private DailyPriceResponse price(LocalDate date) {
        return new DailyPriceResponse(date, 69_000L, 70_500L, 68_800L, 70_000L, 123_456L, 8_610_000_000L);
    }
}
