package com.sajo.trading_service.trading.client.dto.response;

import java.time.OffsetDateTime;

public record MarketStockQuoteResponse(
        String stockCode,
        Long currentPrice,
        Long previousClosePrice,
        OffsetDateTime baseTime
) {
}
