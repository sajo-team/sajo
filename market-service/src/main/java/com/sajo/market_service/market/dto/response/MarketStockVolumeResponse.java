package com.sajo.market_service.market.dto.response;

import java.time.LocalDate;

public record MarketStockVolumeResponse(
        LocalDate tradeDate,
        Long accumulatedVolume,
        Long accumulatedTradeAmount
) {

    public static MarketStockVolumeResponse from(MarketStockPriceResponse price) {
        return new MarketStockVolumeResponse(
                price.tradeDate(),
                price.accumulatedVolume(),
                price.accumulatedTradeAmount()
        );
    }
}
