package com.sajo.market_service.market.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.market.dto.response.MarketStockIndicatorResponse;
import com.sajo.market_service.market.dto.response.QuoteResponse;
import com.sajo.market_service.market.exception.MarketErrorCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketInternalQueryServiceTest {

    @Test
    void reusesExistingIndicatorAndQuoteQueryServices() {
        MarketStockIndicatorQueryService indicatorService = mock(MarketStockIndicatorQueryService.class);
        MarketQuoteQueryService quoteService = mock(MarketQuoteQueryService.class);
        MarketInternalQueryService service = new MarketInternalQueryService(indicatorService, quoteService);
        UUID userId = UUID.randomUUID();
        OffsetDateTime baseTime = OffsetDateTime.parse("2026-09-04T14:30:00+09:00");

        when(indicatorService.getLatestIndicator("005930")).thenReturn(
                new MarketStockIndicatorResponse(LocalDate.of(2026, 9, 3), new BigDecimal("15.2"), new BigDecimal("1.3"), null, null, new BigDecimal("8.7")));
        when(quoteService.getQuote(userId, "005930")).thenReturn(
                new QuoteResponse("005930", 71_800L, null, null, null, null, null, null, null, null, null, null, null, null, null, baseTime.toString()));

        assertThat(service.getIndicator("005930").per()).isEqualByComparingTo("15.2");
        assertThat(service.getQuote(userId, "005930").baseTime()).isEqualTo(baseTime);
        verify(indicatorService).getLatestIndicator("005930");
        verify(quoteService).getQuote(userId, "005930");
    }

    @Test
    void rejectsMissingBaseTimeForInternalQuote() {
        assertInvalidInternalBaseTime(null);
    }

    @Test
    void rejectsBlankBaseTimeForInternalQuote() {
        assertInvalidInternalBaseTime(" ");
    }

    @Test
    void rejectsMalformedBaseTimeForInternalQuote() {
        assertInvalidInternalBaseTime("not-a-date");
    }

    private void assertInvalidInternalBaseTime(String baseTime) {
        MarketStockIndicatorQueryService indicatorService = mock(MarketStockIndicatorQueryService.class);
        MarketQuoteQueryService quoteService = mock(MarketQuoteQueryService.class);
        MarketInternalQueryService service = new MarketInternalQueryService(indicatorService, quoteService);
        UUID userId = UUID.randomUUID();
        when(quoteService.getQuote(userId, "005930")).thenReturn(
                new QuoteResponse("005930", 71_800L, null, null, null, null, null, null, null, null, null, null, null, null, null, baseTime));

        assertThatThrownBy(() -> service.getQuote(userId, "005930"))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(MarketErrorCode.KIS_QUOTE_RESPONSE_INVALID));
    }
}
