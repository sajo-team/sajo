package com.sajo.market_service.market.dto.response;

import com.sajo.market_service.market.domain.FinancialPeriodType;
import com.sajo.market_service.market.domain.MarketStockIndicator;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketStockIndicatorSnapshot(
        BigDecimal per,
        BigDecimal pbr,
        Instant valuationFetchedAt,
        BigDecimal roe,
        FinancialPeriodType financialPeriodType,
        String financialReferenceYearMonth,
        Instant financialFetchedAt
) {
    public static MarketStockIndicatorSnapshot from(MarketStockIndicator indicator) {
        return new MarketStockIndicatorSnapshot(
                indicator.getPer(), indicator.getPbr(), indicator.getValuationFetchedAt(), indicator.getRoe(),
                indicator.getFinancialPeriodType(), indicator.getFinancialReferenceYearMonth(),
                indicator.getFinancialFetchedAt());
    }
}
