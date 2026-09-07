package com.sajo.market_service.market.repository.command;

import com.sajo.market_service.market.dto.command.MarketStockIndicatorCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/** PostgreSQL upsert preserves the original row identity while refreshing one market-date snapshot. */
@Repository
@RequiredArgsConstructor
public class MarketStockIndicatorWriter {

    private static final String UPSERT_INDICATOR = """
            insert into m_market_stocks_indicator (
                id, stock_id, reference_date, per, pbr, eps, bps, roe, created_at, updated_at
            ) values (
                :id, :stockId, :referenceDate, :per, :pbr, :eps, :bps, null, :createdAt, :updatedAt
            ) on conflict (stock_id, reference_date) do update set
                per = excluded.per,
                pbr = excluded.pbr,
                eps = excluded.eps,
                bps = excluded.bps,
                updated_at = excluded.updated_at
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public void upsert(UUID stockId, MarketStockIndicatorCommand indicator) {
        Instant now = Instant.now();
        jdbcTemplate.update(UPSERT_INDICATOR, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("stockId", stockId)
                .addValue("referenceDate", indicator.referenceDate())
                .addValue("per", indicator.per())
                .addValue("pbr", indicator.pbr())
                .addValue("eps", indicator.eps())
                .addValue("bps", indicator.bps())
                .addValue("createdAt", Timestamp.from(now))
                .addValue("updatedAt", Timestamp.from(now)));
    }
}
