package com.sajo.market_service.market.dto.response;

import com.sajo.market_service.market.domain.MarketStockIndicator;
import com.sajo.market_service.market.domain.FinancialPeriodType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;

public record MarketStockIndicatorResponse(
        /** 구 계약 호환 필드다. 신규 분기 스냅샷에서는 null일 수 있다. */
        LocalDate referenceDate,
        BigDecimal per,
        BigDecimal pbr,
        BigDecimal roe,
        Instant valuationFetchedAt,
        FinancialPeriodType financialPeriodType,
        String financialReferenceYearMonth,
        Instant financialFetchedAt
) {

    public static MarketStockIndicatorResponse from(MarketStockIndicator indicator) {
        return new MarketStockIndicatorResponse(
                indicator.getReferenceDate(),
                indicator.getPer(),
                indicator.getPbr(),
                indicator.getRoe(),
                indicator.getValuationFetchedAt(),
                indicator.getFinancialPeriodType(),
                indicator.getFinancialReferenceYearMonth(),
                indicator.getFinancialFetchedAt()
        );
    }
}
