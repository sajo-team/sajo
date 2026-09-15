package com.sajo.trading_service.trading.client;

import com.sajo.common.response.GeneralResponse;
import com.sajo.trading_service.trading.client.dto.response.MarketStockQuoteResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

@FeignClient(
        name = "market-service",
        contextId = "tradingMarketStockClient"
)
public interface MarketStockClient {

    @GetMapping("/internal/v1/stocks/{stockCode}/quote")
    GeneralResponse<MarketStockQuoteResponse> getQuote(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable String stockCode
    );
}
