package com.sajo.market_service.strategy.client.market.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MarketStockIndicatorResponse(
        String stockCode,
        BigDecimal per,
        BigDecimal pbr,
        BigDecimal roe,
        LocalDate referenceDate
) {
}
