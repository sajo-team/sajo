package com.sajo.market_service.strategy.client.market;


import com.sajo.market_service.strategy.client.market.dto.MarketStockIndicatorResponse;
import com.sajo.market_service.strategy.client.market.dto.MarketStockQuoteResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

@FeignClient(
        name = "market-service",
        contextId = "strategyMarketStockFeignClient",
        path = "/internal/v1/stocks"
)
public interface MarketStockFeignClient {

    @GetMapping("/{stockCode}/indicator")
    MarketStockIndicatorResponse getMarketStockIndicator(
            @PathVariable("stockCode") String stockCode
    );

    @GetMapping("/{stockCode}/quote")
    MarketStockQuoteResponse getMarketStockQuote(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable("stockCode") String stockCode
    );
}
