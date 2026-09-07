package com.sajo.market_service.market.repository.query;

import java.util.UUID;

/** Scheduler collection requires only the stable identifier and stock code. */
public interface MarketStockCollectionTarget {

    UUID getStockId();

    String getStockCode();
}
