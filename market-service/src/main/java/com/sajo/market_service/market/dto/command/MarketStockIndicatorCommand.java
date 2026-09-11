package com.sajo.market_service.market.dto.command;

import com.sajo.market_service.market.dto.response.QuoteResponse;

import java.math.BigDecimal;
import com.sajo.market_service.market.domain.FinancialPeriodType;
import com.sajo.market_service.market.dto.response.FinancialRatioResponse;

import java.time.Instant;
import java.time.YearMonth;
import java.util.Optional;

/**
 * 현재가 평가 지표와 분기 재무비율을 서로의 수신 시각·결산연월과 함께 담는 저장 요청 DTO
 * */
public record MarketStockIndicatorCommand(
        BigDecimal per,
        BigDecimal pbr,
        Instant valuationFetchedAt,
        BigDecimal roe,
        FinancialPeriodType financialPeriodType,
        YearMonth financialReferenceYearMonth,
        Instant financialFetchedAt
) {

    public static Optional<MarketStockIndicatorCommand> from(
            QuoteResponse quote,
            FinancialRatioResponse financialRatio
    ) {
        if (quote == null || quote.fetchedAt() == null || financialRatio == null
                || financialRatio.financialReferenceYearMonth() == null
                || financialRatio.fetchedAt() == null || financialRatio.roe() == null) {
            return Optional.empty();
        }
        if (quote.per() == null && quote.pbr() == null) {
            return Optional.empty();
        }
        return Optional.of(new MarketStockIndicatorCommand(
                quote.per(), quote.pbr(), quote.fetchedAt(),
                financialRatio.roe(), FinancialPeriodType.QUARTER,
                financialRatio.financialReferenceYearMonth(), financialRatio.fetchedAt()));
    }
}
