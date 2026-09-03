package com.sajo.market_service.strategy.client.market;


import com.sajo.market_service.strategy.client.market.dto.MarketStockFinancialIndicatorResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "market-service",
        contextId = "strategyMarketStockFeignClient",
        path = "/internal/v1/stocks"
)
public interface MarketStockFeignClient {

    @GetMapping("/{stockCode}/financial-indicator")
    MarketStockFinancialIndicatorResponse getMarketStockFinancialIndicator(
            @PathVariable("stockCode") String stockCode
    );
}
