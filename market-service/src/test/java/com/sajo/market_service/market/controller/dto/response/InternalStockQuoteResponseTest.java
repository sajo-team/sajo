package com.sajo.market_service.market.controller.dto.response;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.market.dto.response.QuoteResponse;
import com.sajo.market_service.market.exception.MarketErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalStockQuoteResponseTest {

    private static final String STOCK_CODE = "005930";
    private static final String BASE_TIME = "2026-09-04T14:30:00+09:00";

    @Test
    @DisplayName("QuoteResponse의 previousClosePrice를 그대로 내부 응답에 매핑한다(#228, Trading 주문 전 가격 검증용)")
    void mapsPreviousClosePriceFromQuoteResponse() {
        QuoteResponse quote = new QuoteResponse(
                STOCK_CODE, 71_800L, 70_000L, 72_000L, 69_500L, 70_500L,
                1_300L, null, null, null, null, null, null, null, null, BASE_TIME);

        InternalStockQuoteResponse response = InternalStockQuoteResponse.from(quote);

        assertThat(response.stockCode()).isEqualTo(STOCK_CODE);
        assertThat(response.currentPrice()).isEqualTo(71_800L);
        assertThat(response.previousClosePrice()).isEqualTo(70_500L);
        assertThat(response.baseTime()).isEqualTo(OffsetDateTime.parse(BASE_TIME));
    }

    @Test
    @DisplayName("previousClosePrice가 null인 QuoteResponse도 그대로 null로 매핑한다(baseTime만 필수)")
    void mapsNullPreviousClosePriceWithoutThrowing() {
        QuoteResponse quote = new QuoteResponse(
                STOCK_CODE, 71_800L, null, null, null, null, null, null, null, null, null, null, null, null, null, BASE_TIME);

        InternalStockQuoteResponse response = InternalStockQuoteResponse.from(quote);

        assertThat(response.previousClosePrice()).isNull();
    }

    @Test
    @DisplayName("baseTime이 없으면 previousClosePrice 유무와 무관하게 예외를 던진다")
    void throwsWhenBaseTimeIsMissingRegardlessOfPreviousClosePrice() {
        QuoteResponse quote = new QuoteResponse(
                STOCK_CODE, 71_800L, null, null, null, 70_500L, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> InternalStockQuoteResponse.from(quote))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(MarketErrorCode.KIS_QUOTE_RESPONSE_INVALID));
    }
}
