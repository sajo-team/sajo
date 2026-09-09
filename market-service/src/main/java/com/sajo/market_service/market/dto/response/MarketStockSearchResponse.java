package com.sajo.market_service.market.dto.response;

import com.sajo.market_service.market.domain.MarketStock;

import java.math.BigDecimal;
import java.util.UUID;

/** 종목 선택 후 Strategy 생성에 사용할 검색 응답이다. */
public record MarketStockSearchResponse(
        UUID stockId,
        String stockCode,
        String stockName,
        String marketType,
        String industryCode,
        Long listedShares,
        BigDecimal marketCap
) {
    public static MarketStockSearchResponse from(MarketStock stock) {
        return new MarketStockSearchResponse(
                stock.getId(),
                stock.getStockCode(),
                stock.getStockName(),
                stock.getMarketType(),
                stock.getIndustryCode(),
                stock.getListedShares(),
                stock.getMarketCap()
        );
    }
}
