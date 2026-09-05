package com.sajo.market_service.market.controller.dto.response;

import com.sajo.market_service.market.dto.response.MarketStockIndicatorResponse;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InternalStockIndicatorResponse(
        String stockCode,
        BigDecimal per,
        BigDecimal pbr,
        BigDecimal roe,
        LocalDate referenceDate
) {

    public static InternalStockIndicatorResponse from(String stockCode, MarketStockIndicatorResponse indicator) {
        return new InternalStockIndicatorResponse(
                stockCode, indicator.per(), indicator.pbr(), indicator.roe(), indicator.referenceDate());
    }
}
