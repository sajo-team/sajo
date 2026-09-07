package com.sajo.market_service.market.repository.command;

import com.sajo.market_service.market.dto.response.DailyPriceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST API 일봉 가격 데이터를 빠르게 저장하고,
 * 중복 데이터는 DB 레벨에서 안전하게 무시하는 저장 전용 컴포넌트
 *
 * 개선 사항 : Partial Unique Index + ON CONFLICT DO NOTHING으로 동시 중복 방지
 */
@Repository
@RequiredArgsConstructor
public class MarketStockPriceDailyRestWriter {

    private static final String INSERT_DAILY_REST_PRICE = """
            insert into m_market_stocks_price (
                id, stock_id, date, time, current_price, close_price, open_price, high_price, low_price,
                prev_close_price, change_price, change_rate, volume, accumulated_volume,
                accumulated_trade_amount, foreign_ownership_rate, source, created_at
            ) values (
                :id, :stockId, :date, null, null, :closePrice, :openPrice, :highPrice, :lowPrice,
                null, null, null, null, :accumulatedVolume, :accumulatedTradeAmount, null, 'REST', :createdAt
            ) on conflict (stock_id, date) where time is null and source = 'REST' do nothing
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public int insertIgnoringDuplicates(UUID stockId, List<DailyPriceResponse> prices) {
        if (prices.isEmpty()) {
            return 0;
        }
        Instant createdAt = Instant.now();
        MapSqlParameterSource[] parameters = prices.stream()
                .map(price -> toParameters(stockId, price, createdAt))
                .toArray(MapSqlParameterSource[]::new);
        int[] updateCounts = jdbcTemplate.batchUpdate(INSERT_DAILY_REST_PRICE, parameters);
        // SUCCESS_NO_INFO(-2) does not represent an exact inserted-row count, so exclude it from the returned count.
        // Callers must not use this value as a correctness decision; ON CONFLICT is the final duplicate guard.
        return java.util.Arrays.stream(updateCounts)
                .filter(count -> count != Statement.SUCCESS_NO_INFO)
                .map(count -> Math.max(count, 0))
                .sum();
    }

    private MapSqlParameterSource toParameters(UUID stockId, DailyPriceResponse price, Instant createdAt) {
        return new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("stockId", stockId)
                .addValue("date", price.tradeDate())
                .addValue("closePrice", price.closePrice())
                .addValue("openPrice", price.openPrice())
                .addValue("highPrice", price.highPrice())
                .addValue("lowPrice", price.lowPrice())
                .addValue("accumulatedVolume", price.volume())
                .addValue("accumulatedTradeAmount", price.tradeAmount())
                .addValue("createdAt", Timestamp.from(createdAt));
    }
}