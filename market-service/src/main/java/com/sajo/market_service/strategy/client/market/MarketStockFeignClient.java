package com.sajo.market_service.strategy.client.market;


import com.sajo.market_service.strategy.client.market.dto.MarketStockIndicatorResponse;
import com.sajo.market_service.strategy.client.market.dto.MarketStockQuoteResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

/**
 * Strategy와 Market이 동일 애플리케이션에 구성되어 있어
 * 현재는 MarketInternalQueryService를 직접 호출한다.
 *
 * 추후 Market이 별도 서비스로 분리될 경우
 * 내부 API 호출용 FeignClient로 전환할 수 있도록 보관한다.
 */
@FeignClient(
        name = "market-service",
        contextId = "strategyMarketStockFeignClient"
)
public interface MarketStockFeignClient {

    @GetMapping("/internal/v1/stocks/{stockCode}/indicator")
    MarketStockIndicatorResponse getMarketStockIndicator(
            @PathVariable("stockCode") String stockCode
    );

    @GetMapping("/internal/v1/stocks/{stockCode}/quote")
    MarketStockQuoteResponse getMarketStockQuote(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable("stockCode") String stockCode
    );
}
