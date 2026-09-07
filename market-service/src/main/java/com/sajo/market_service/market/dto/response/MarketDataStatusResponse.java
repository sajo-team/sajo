package com.sajo.market_service.market.dto.response;

import java.time.LocalDate;

public record MarketDataStatusResponse(
        long totalStockCount,
        long dailyPriceStockCount,
        LocalDate latestDailyPriceDate,
        long indicatorStockCount,
        LocalDate latestIndicatorReferenceDate
) {
}
