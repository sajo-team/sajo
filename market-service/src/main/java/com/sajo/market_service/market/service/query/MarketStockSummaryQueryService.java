package com.sajo.market_service.market.service.query;

import com.sajo.market_service.market.domain.MarketStock;
import com.sajo.market_service.market.dto.response.MarketStockIndicatorResponse;
import com.sajo.market_service.market.dto.response.MarketStockResponse;
import com.sajo.market_service.market.dto.response.MarketStockSummaryResponse;
import com.sajo.market_service.market.dto.response.PublicQuoteResponse;
import com.sajo.market_service.market.dto.response.QuoteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MarketStockSummaryQueryService {

    private final MarketStockQueryService marketStockQueryService;
    private final MarketQuoteQueryService marketQuoteQueryService;
    private final MarketStockIndicatorQueryService marketStockIndicatorQueryService;

    public MarketStockSummaryResponse getSummary(UUID userId, String stockCode) {
        String normalizedStockCode = MarketStock.normalizeStockCode(stockCode);
        MarketStockQueryTarget stockTarget = marketStockQueryService.getStockTarget(normalizedStockCode);
        QuoteResponse quote = marketQuoteQueryService.getQuote(userId, normalizedStockCode);
        MarketStockIndicatorResponse indicator = marketStockIndicatorQueryService
                .findLatestIndicatorByConfirmedStockId(stockTarget.stockId())
                .orElse(null);

        return new MarketStockSummaryResponse(stockTarget.response(), PublicQuoteResponse.from(quote), indicator);
    }
}
