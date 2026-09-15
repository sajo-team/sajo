package com.sajo.market_service.market.controller.dto.response;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.market.dto.response.QuoteResponse;
import com.sajo.market_service.market.exception.MarketErrorCode;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

public record InternalStockQuoteResponse(
        String stockCode,
        Long currentPrice,
        Long previousClosePrice,
        OffsetDateTime baseTime
    ) {

    /**
     * previousClosePrice는 Trading의 주문 전 상·하한가 검증에 쓰인다(#228).
     * WebSocket이 REST 보다 먼저 캐시를 채운 종목은 이 값이 null일 수 있는데,
     * {@code MarketQuoteQueryService.isCacheableQuote}가 previousClosePrice가 없는 캐시 항목을 "재사용 불가"로 취급해 KIS REST를 강제로 재조회하도록 보장하므로,
     * 이 API 레벨에서는 별도 null 방어를 추가하지 않는다.
     */
    public static InternalStockQuoteResponse from(QuoteResponse quote) {
        String baseTime = quote.baseTime();
        if (baseTime == null || baseTime.isBlank()) {
            throw new BusinessException(MarketErrorCode.KIS_QUOTE_RESPONSE_INVALID,
                    "KIS 현재가 기준 시각이 없습니다.");
        }
        try {
            return new InternalStockQuoteResponse(
                    quote.stockCode(), quote.currentPrice(), quote.previousClosePrice(), OffsetDateTime.parse(baseTime));
        } catch (DateTimeParseException exception) {
            throw new BusinessException(MarketErrorCode.KIS_QUOTE_RESPONSE_INVALID,
                    "KIS 현재가 기준 시각이 유효하지 않습니다.");
        }
    }
}
