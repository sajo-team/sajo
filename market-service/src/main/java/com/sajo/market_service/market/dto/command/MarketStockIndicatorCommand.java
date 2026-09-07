package com.sajo.market_service.market.dto.command;

import com.sajo.market_service.market.dto.response.QuoteResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * KIS 현재가 응답에서 투자지표 저장에 필요한 날짜·PER·PBR·EPS·BPS만 골라 담는 저장 요청 DTO
 *
 * TODO : MVP에선 ROE 데이터 보류
 * */
public record MarketStockIndicatorCommand(
        LocalDate referenceDate,
        BigDecimal per,
        BigDecimal pbr,
        BigDecimal eps,
        BigDecimal bps
) {

    public static Optional<MarketStockIndicatorCommand> from(QuoteResponse quote) {
        // KIS 실제 영업일이 없으면 저장하지 않는다.
        if (quote.businessDate() == null) {
            return Optional.empty();
        }
        //PER과 PBR이 둘 다 없으면
        if (quote.per() == null && quote.pbr() == null) {
            return Optional.empty();
        }
        return Optional.of(new MarketStockIndicatorCommand(
                quote.businessDate(), quote.per(), quote.pbr(), quote.eps(), quote.bps()));
    }
}
