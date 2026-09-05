package com.sajo.market_service.market.controller.dto.response;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.market.dto.response.QuoteResponse;
import com.sajo.market_service.market.exception.MarketErrorCode;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

public record InternalStockQuoteResponse(
        String stockCode,
        Long currentPrice,
        OffsetDateTime baseTime
    ) {

    public static InternalStockQuoteResponse from(QuoteResponse quote) {
        String baseTime = quote.baseTime();
        if (baseTime == null || baseTime.isBlank()) {
            throw new BusinessException(MarketErrorCode.KIS_QUOTE_RESPONSE_INVALID,
                    "KIS 현재가 기준 시각이 없습니다.");
        }
        try {
            return new InternalStockQuoteResponse(quote.stockCode(), quote.currentPrice(), OffsetDateTime.parse(baseTime));
        } catch (DateTimeParseException exception) {
            throw new BusinessException(MarketErrorCode.KIS_QUOTE_RESPONSE_INVALID,
                    "KIS 현재가 기준 시각이 유효하지 않습니다.");
        }
    }
}
