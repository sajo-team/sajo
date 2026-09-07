package com.sajo.market_service.market.dto.response;

/** 종목 기본정보, 현재가, 저장된 최신 투자지표를 조합한 조회 응답이다. */
public record MarketStockSummaryResponse(
        MarketStockResponse stock,
        PublicQuoteResponse quote,
        MarketStockIndicatorResponse indicator
) {
}
