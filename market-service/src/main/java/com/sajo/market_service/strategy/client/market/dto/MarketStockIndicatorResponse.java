package com.sajo.market_service.strategy.client.market.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Market 서비스 분리 이후 Feign 응답 계약으로 사용할 DTO.
 * 현재 Strategy에서는 사용하지 않고 MarketInternalQueryService를 직접 호출한다.
 */
public record MarketStockIndicatorResponse(
        String stockCode,
        BigDecimal per,
        BigDecimal pbr,
        BigDecimal roe,
        LocalDate referenceDate
) {
}
