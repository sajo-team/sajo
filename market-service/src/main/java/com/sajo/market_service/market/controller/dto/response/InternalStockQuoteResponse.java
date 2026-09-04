package com.sajo.market_service.market.controller.dto.response;

import com.sajo.market_service.market.dto.response.QuoteResponse;

import java.time.OffsetDateTime;

public record InternalStockQuoteResponse(
        String stockCode,
        Long currentPrice,
        OffsetDateTime baseTime
) {

    public static InternalStockQuoteResponse from(QuoteResponse quote) {
        return new InternalStockQuoteResponse(quote.stockCode(), quote.currentPrice(), OffsetDateTime.parse(quote.baseTime()));
    }
}
