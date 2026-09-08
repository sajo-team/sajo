package com.sajo.market_service.strategy.service.command;

import com.sajo.market_service.market.dto.response.MarketStockPriceResponse;
import com.sajo.market_service.market.service.query.MarketStockPriceQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class BacktestPriceReader {
    // Market의 백테스트 기간에 맞는 기존 일별 시세 조회 기능 호출

    private final MarketStockPriceQueryService marketStockPriceQueryService;

    public List<MarketStockPriceResponse> read (
            String stockCode,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return marketStockPriceQueryService
                .getDailyPrices(stockCode, startDate, endDate);
    }
}
