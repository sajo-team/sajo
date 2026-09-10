package com.sajo.market_service.market.controller.dto.response;

import com.sajo.market_service.market.domain.FinancialPeriodType;
import com.sajo.market_service.market.dto.response.MarketStockIndicatorSnapshot;

import java.math.BigDecimal;
import java.time.Instant;

public record InternalStockIndicatorResponse(
        String stockCode,
        BigDecimal per,
        BigDecimal pbr,
        Instant valuationFetchedAt,
        BigDecimal roe,
        FinancialPeriodType financialPeriodType,
        String financialReferenceYearMonth,
        Instant financialFetchedAt
) {

    public static InternalStockIndicatorResponse from(String stockCode, MarketStockIndicatorSnapshot indicator) {
        return new InternalStockIndicatorResponse(
                stockCode, indicator.per(), indicator.pbr(), indicator.valuationFetchedAt(), indicator.roe(),
                indicator.financialPeriodType(), indicator.financialReferenceYearMonth(), indicator.financialFetchedAt());
    }
}
