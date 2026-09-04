package com.sajo.market_service.market.dto.response;

import com.sajo.market_service.market.domain.MarketStockIndicator;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MarketStockIndicatorResponse(
        LocalDate referenceDate,
        BigDecimal per,
        BigDecimal pbr,
        BigDecimal eps,
        BigDecimal bps,
        BigDecimal roe
) {

    public static MarketStockIndicatorResponse from(MarketStockIndicator indicator) {
        return new MarketStockIndicatorResponse(
                indicator.getReferenceDate(),
                indicator.getPer(),
                indicator.getPbr(),
                indicator.getEps(),
                indicator.getBps(),
                indicator.getRoe()
        );
    }
}
