package com.sajo.market_service.market.dto.response;

import com.sajo.market_service.market.domain.MarketStock;

import java.math.BigDecimal;

public record MarketStockResponse(
        String stockCode,
        String stockName,
        String marketType,
        String industryCode,
        Long listedShares,
        BigDecimal marketCap
) {
    public static MarketStockResponse from(MarketStock stock) {
        return new MarketStockResponse(
                stock.getStockCode(),
                stock.getStockName(),
                stock.getMarketType(),
                stock.getIndustryCode(),
                stock.getListedShares(),
                stock.getMarketCap()
        );
    }
}
