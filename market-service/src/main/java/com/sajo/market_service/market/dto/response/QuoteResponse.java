package com.sajo.market_service.market.dto.response;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.market.dto.kis.KisQuoteResponse;
import com.sajo.market_service.market.exception.MarketErrorCode;

import java.math.BigDecimal;

/** Market 내부에서 사용하는 현재가 응답 모델이다. */
public record QuoteResponse(
        String stockCode,
        Long currentPrice,
        Long openPrice,
        Long highPrice,
        Long lowPrice,
        Long previousClosePrice,
        Long changePrice,
        BigDecimal changeRate,
        Long accumulatedVolume,
        Long tradeAmount,
        Long marketCapitalization,
        BigDecimal per,
        BigDecimal pbr,
        BigDecimal eps,
        BigDecimal bps
) {

    public static QuoteResponse from(KisQuoteResponse response, String stockCode) {
        if (response == null) {
            throw new BusinessException(
                    MarketErrorCode.KIS_QUOTE_RESPONSE_INVALID,
                    "KIS 현재가 응답이 비어 있습니다."
            );
        }
        KisQuoteResponse.KisQuoteOutput output = response.output();
        if (output == null) {
            throw new BusinessException(
                    MarketErrorCode.KIS_QUOTE_RESPONSE_INVALID,
                    "KIS 현재가 응답에 output이 없습니다."
            );
        }
        return new QuoteResponse(
                stockCode,
                toLong(output.currentPrice()),
                toLong(output.openPrice()),
                toLong(output.highPrice()),
                toLong(output.lowPrice()),
                toLong(output.previousClosePrice()),
                toLong(output.changePrice()),
                toBigDecimal(output.changeRate()),
                toLong(output.accumulatedVolume()),
                toLong(output.tradeAmount()),
                toLong(output.marketCapitalization()),
                toBigDecimal(output.per()),
                toBigDecimal(output.pbr()),
                toBigDecimal(output.eps()),
                toBigDecimal(output.bps())
        );
    }

    private static Long toLong(String value) {
        return value == null || value.isBlank() ? null : Long.valueOf(value);
    }

    private static BigDecimal toBigDecimal(String value) {
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }
}
