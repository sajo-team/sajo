package com.sajo.market_service.market.repository.query;

import java.time.LocalDate;

public interface MarketDataStatusProjection {

    Long getTotalStockCount();

    Long getDailyPriceStockCount();

    LocalDate getLatestDailyPriceDate();

    Long getIndicatorStockCount();

    LocalDate getLatestIndicatorReferenceDate();
}
