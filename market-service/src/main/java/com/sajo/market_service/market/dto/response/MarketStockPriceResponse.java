package com.sajo.market_service.market.dto.response;

import com.sajo.market_service.market.domain.MarketStockPrice;

import java.time.LocalDate;

public record MarketStockPriceResponse(
        LocalDate tradeDate,
        Long openPrice,
        Long highPrice,
        Long lowPrice,
        Long closePrice,
        Long accumulatedVolume,
        Long accumulatedTradeAmount
) {

    public static MarketStockPriceResponse from(MarketStockPrice price) {
        return new MarketStockPriceResponse(
                price.getDate(),
                price.getOpenPrice(),
                price.getHighPrice(),
                price.getLowPrice(),
                price.getClosePrice(),
                price.getAccumulatedVolume(),
                price.getAccumulatedTradeAmount()
        );
    }
}
