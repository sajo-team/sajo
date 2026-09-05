package com.sajo.market_service.market.service.query;

import com.sajo.market_service.market.controller.dto.response.InternalStockIndicatorResponse;
import com.sajo.market_service.market.controller.dto.response.InternalStockQuoteResponse;
import com.sajo.market_service.market.domain.MarketStock;
import com.sajo.market_service.market.dto.response.MarketStockIndicatorResponse;
import com.sajo.market_service.market.dto.response.QuoteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MarketInternalQueryService {

    private final MarketStockIndicatorQueryService marketStockIndicatorQueryService;
    private final MarketQuoteQueryService marketQuoteQueryService;

    public InternalStockIndicatorResponse getIndicator(String stockCode) {
        String normalizedStockCode = MarketStock.normalizeStockCode(stockCode);
        MarketStockIndicatorResponse indicator = marketStockIndicatorQueryService.getLatestIndicator(normalizedStockCode);
        return InternalStockIndicatorResponse.from(normalizedStockCode, indicator);
    }

    public InternalStockQuoteResponse getQuote(UUID userId, String stockCode) {
        String normalizedStockCode = MarketStock.normalizeStockCode(stockCode);
        QuoteResponse quote = marketQuoteQueryService.getQuote(userId, normalizedStockCode);
        return InternalStockQuoteResponse.from(quote);
    }
}
