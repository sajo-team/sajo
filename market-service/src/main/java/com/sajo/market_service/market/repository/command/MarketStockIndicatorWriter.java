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
                id, stock_id, reference_date, per, pbr, eps, bps, valuation_fetched_at,
                roe, financial_period_type, financial_reference_year_month, financial_fetched_at,
                created_at, updated_at
            ) values (
                :id, :stockId, null, :per, :pbr, null, null, :valuationFetchedAt,
                :roe, :financialPeriodType, :financialReferenceYearMonth, :financialFetchedAt,
                :createdAt, :updatedAt
            ) on conflict (stock_id, financial_period_type, financial_reference_year_month)
              where financial_period_type is not null and financial_reference_year_month is not null
            do update set
                -- PER/PBR은 하나의 현재가 응답 스냅샷이므로 둘 다 있을 때만 함께 갱신한다.
                per = case when excluded.per is not null and excluded.pbr is not null
                    then excluded.per else m_market_stocks_indicator.per end,
                pbr = case when excluded.per is not null and excluded.pbr is not null
                    then excluded.pbr else m_market_stocks_indicator.pbr end,
                valuation_fetched_at = case when excluded.per is not null and excluded.pbr is not null
                    then excluded.valuation_fetched_at else m_market_stocks_indicator.valuation_fetched_at end,
                roe = coalesce(excluded.roe, m_market_stocks_indicator.roe),
                financial_fetched_at = excluded.financial_fetched_at,
                updated_at = excluded.updated_at
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public void upsert(UUID stockId, MarketStockIndicatorCommand indicator) {
        // updated_at은 JPA Auditing이 아니라 마지막 정상 upsert 시각을 기록한다.
        Instant now = Instant.now();
        jdbcTemplate.update(UPSERT_INDICATOR, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("stockId", stockId)
                .addValue("per", indicator.per())
                .addValue("pbr", indicator.pbr())
                .addValue("valuationFetchedAt", Timestamp.from(indicator.valuationFetchedAt()))
                .addValue("roe", indicator.roe())
                .addValue("financialPeriodType", indicator.financialPeriodType().name())
                .addValue("financialReferenceYearMonth", indicator.financialReferenceYearMonth().toString())
                .addValue("financialFetchedAt", Timestamp.from(indicator.financialFetchedAt()))
                .addValue("createdAt", Timestamp.from(now))
                .addValue("updatedAt", Timestamp.from(now)));
    }
}
