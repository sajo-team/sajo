package com.sajo.market_service.strategy.client.market.dto;

import java.time.OffsetDateTime;

public record MarketStockQuoteResponse(
        String stockCode,
        Long currentPrice,
        OffsetDateTime baseTime
) {
}
