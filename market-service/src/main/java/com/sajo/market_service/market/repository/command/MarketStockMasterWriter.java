package com.sajo.market_service.market.repository.command;

import com.sajo.market_service.market.dto.command.MarketStockMasterCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * PostgreSQL에 종목 마스터 정보를 batch upsert하는 Writer
 */
@Repository
@RequiredArgsConstructor
public class MarketStockMasterWriter {

    //on conflict (stock_code) do update set : 이미 존재하는 종목 갱신
    //excluded : Upsert
    private static final String UPSERT_MARKET_STOCK = """
            insert into m_market_stocks (
                id, stock_code, stock_name, market_type, industry_code, listed_shares, market_cap, created_at, updated_at
            ) values (
                :id, :stockCode, :stockName, :marketType, :industryCode, :listedShares, :marketCap, :now, :now
            ) on conflict (stock_code) do update set
                stock_name = excluded.stock_name,
                market_type = excluded.market_type,
                industry_code = excluded.industry_code,
                listed_shares = excluded.listed_shares,
                market_cap = excluded.market_cap,
                updated_at = excluded.updated_at
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /** PostgreSQL에서 Upsert를 실행할 때, 기존의 id와 created_at은 그대로 유지하고 stock_code가 중복되면 나머지 값만 업데이트 */
    public int upsertAll(List<MarketStockMasterCommand> stocks) {
        //같은 배치에 포함된 종목들은 동일한 시간을 사용
        Instant now = Instant.now();
        MapSqlParameterSource[] parameters = stocks.stream()
                .map(stock -> toParameters(stock, now))
                .toArray(MapSqlParameterSource[]::new);
        //batch : 여러 INSERT 작업을 묶어서 전달
        int[] updateCounts = jdbcTemplate.batchUpdate(UPSERT_MARKET_STOCK, parameters);
        // SUCCESS_NO_INFO (-2) is not an exact saved-row count, so callers must not use this return value for decisions.
        return java.util.Arrays.stream(updateCounts).map(count -> Math.max(count, 0)).sum();
    }

    private MapSqlParameterSource toParameters(MarketStockMasterCommand stock, Instant now) {
        return new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("stockCode", stock.stockCode())
                .addValue("stockName", stock.stockName())
                .addValue("marketType", stock.marketType())
                .addValue("industryCode", stock.industryCode())
                .addValue("listedShares", stock.listedShares())
                .addValue("marketCap", stock.marketCap())
                .addValue("now", now);
    }
}
