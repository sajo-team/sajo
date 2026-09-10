package com.sajo.market_service.market.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.market.dto.response.MarketStockIndicatorResponse;
import com.sajo.market_service.market.dto.response.MarketStockResponse;
import com.sajo.market_service.market.dto.response.QuoteResponse;
import com.sajo.market_service.market.exception.MarketErrorCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketStockSummaryQueryServiceTest {

    @Test
    void combinesStockQuoteAndIndicatorUsingExistingQueryServices() {
        MarketStockQueryService stockService = mock(MarketStockQueryService.class);
        MarketQuoteQueryService quoteService = mock(MarketQuoteQueryService.class);
        MarketStockIndicatorQueryService indicatorService = mock(MarketStockIndicatorQueryService.class);
        MarketStockSummaryQueryService service = new MarketStockSummaryQueryService(stockService, quoteService, indicatorService);
        UUID userId = UUID.randomUUID();

        when(quoteService.getQuote(userId, "005930"))
                .thenReturn(new QuoteResponse("005930", 71_800L, null, null, null, null, null, null,
                        null, null, null, null, null, null, null));
        MarketStockIndicatorResponse indicator = new MarketStockIndicatorResponse(
                LocalDate.of(2026, 9, 3), new BigDecimal("15.2"), new BigDecimal("1.3"),
                new BigDecimal("4605"), new BigDecimal("51850"), null, null, null, null, null);
        UUID stockId = UUID.randomUUID();
        when(stockService.getStockTarget("005930"))
                .thenReturn(new MarketStockQueryTarget(stockId,
                        new MarketStockResponse("005930", "삼성전자", "KOSPI", null, null, null)));
        when(indicatorService.findLatestIndicatorByConfirmedStockId(stockId)).thenReturn(Optional.of(indicator));

        var response = service.getSummary(userId, " 005930 ");

        assertThat(response.stock().stockName()).isEqualTo("삼성전자");
        assertThat(response.quote().currentPrice()).isEqualTo(71_800L);
        assertThat(response.indicator()).isEqualTo(indicator);
        verify(stockService).getStockTarget("005930");
        verify(quoteService).getQuote(userId, "005930");
        verify(indicatorService).findLatestIndicatorByConfirmedStockId(stockId);
    }

    @Test
    void returnsNullIndicatorWhenNoStoredIndicatorExists() {
        MarketStockQueryService stockService = mock(MarketStockQueryService.class);
        MarketQuoteQueryService quoteService = mock(MarketQuoteQueryService.class);
        MarketStockIndicatorQueryService indicatorService = mock(MarketStockIndicatorQueryService.class);
        MarketStockSummaryQueryService service = new MarketStockSummaryQueryService(stockService, quoteService, indicatorService);
        UUID userId = UUID.randomUUID();

        UUID stockId = UUID.randomUUID();
        when(stockService.getStockTarget("005930"))
                .thenReturn(new MarketStockQueryTarget(stockId,
                        new MarketStockResponse("005930", "삼성전자", "KOSPI", null, null, null)));
        when(quoteService.getQuote(userId, "005930"))
                .thenReturn(new QuoteResponse("005930", 71_800L, null, null, null, null, null, null,
                        null, null, null, null, null, null, null));
        when(indicatorService.findLatestIndicatorByConfirmedStockId(stockId)).thenReturn(Optional.empty());

        assertThat(service.getSummary(userId, "005930").indicator()).isNull();
    }

    @Test
    void propagatesStockNotFound() {
        MarketStockQueryService stockService = mock(MarketStockQueryService.class);
        MarketQuoteQueryService quoteService = mock(MarketQuoteQueryService.class);
        MarketStockIndicatorQueryService indicatorService = mock(MarketStockIndicatorQueryService.class);
        MarketStockSummaryQueryService service = new MarketStockSummaryQueryService(stockService, quoteService, indicatorService);
        UUID userId = UUID.randomUUID();
        BusinessException stockFailure = new BusinessException(MarketErrorCode.MARKET_STOCK_NOT_FOUND);
        when(stockService.getStockTarget("005930")).thenThrow(stockFailure);
        assertThatThrownBy(() -> service.getSummary(userId, "005930")).isSameAs(stockFailure);
    }

    @Test
    void propagatesQuoteFailure() {
        MarketStockQueryService stockService = mock(MarketStockQueryService.class);
        MarketQuoteQueryService quoteService = mock(MarketQuoteQueryService.class);
        MarketStockIndicatorQueryService indicatorService = mock(MarketStockIndicatorQueryService.class);
        MarketStockSummaryQueryService service = new MarketStockSummaryQueryService(stockService, quoteService, indicatorService);
        UUID userId = UUID.randomUUID();
        UUID stockId = UUID.randomUUID();
        when(stockService.getStockTarget("005930"))
                .thenReturn(new MarketStockQueryTarget(stockId,
                        new MarketStockResponse("005930", "삼성전자", "KOSPI", null, null, null)));
        BusinessException failure = new BusinessException(MarketErrorCode.KIS_QUOTE_RESPONSE_INVALID);
        when(quoteService.getQuote(userId, "005930")).thenThrow(failure);
        assertThatThrownBy(() -> service.getSummary(userId, "005930")).isSameAs(failure);
    }
}
