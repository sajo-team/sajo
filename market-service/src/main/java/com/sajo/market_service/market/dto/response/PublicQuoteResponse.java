package com.sajo.market_service.market.dto.response;

import java.math.BigDecimal;

/** Public /quote contract. Cache metadata such as baseTime is intentionally excluded. */
public record PublicQuoteResponse(
        String stockCode,
        Long currentPrice,
        Long openPrice,
        Long highPrice,
        Long lowPrice,
        Long previousClosePrice,
        Long changePrice,
        BigDecimal changeRate,
        Long accumulatedVolume,
        Long tradeAmount,
        Long marketCapitalization,
        BigDecimal per,
        BigDecimal pbr,
        BigDecimal eps,
        BigDecimal bps
) {

    public static PublicQuoteResponse from(QuoteResponse quote) {
        return new PublicQuoteResponse(
                quote.stockCode(), quote.currentPrice(), quote.openPrice(), quote.highPrice(), quote.lowPrice(),
                quote.previousClosePrice(), quote.changePrice(), quote.changeRate(), quote.accumulatedVolume(),
                quote.tradeAmount(), quote.marketCapitalization(), quote.per(), quote.pbr(), quote.eps(), quote.bps());
    }
}
