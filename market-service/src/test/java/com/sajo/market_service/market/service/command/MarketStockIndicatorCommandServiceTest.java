package com.sajo.market_service.market.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.market.client.kis.KisApiClient;
import com.sajo.market_service.market.client.user.UserAccountFeignClient;
import com.sajo.market_service.market.client.user.dto.UserKisTokenResponse;
import com.sajo.market_service.market.dto.response.FinancialRatioResponse;
import com.sajo.market_service.market.dto.response.QuoteResponse;
import com.sajo.market_service.market.exception.MarketErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketStockIndicatorCommandServiceTest {
    @Mock UserAccountFeignClient userClient;
    @Mock KisApiClient kisClient;
    @Mock MarketStockIndicatorPersistenceService persistence;

    @Test
    void callsBothKisApisThroughLimiterAndSavesCombinedSnapshot() {
        var service = new MarketStockIndicatorCommandService(userClient, kisClient, persistence);
        var credentials = credentials();
        UUID stockId = UUID.randomUUID();
        when(kisClient.getQuote(credentials, "005930")).thenReturn(quote());
        when(kisClient.getLatestQuarterlyFinancialRatio(credentials, "005930")).thenReturn(Optional.of(financial()));
        AtomicInteger permits = new AtomicInteger();
        var result = service.collectAndSaveIndicatorsForIdentifiedStock(
                credentials, stockId, "005930", () -> { permits.incrementAndGet(); return true; });
        assertThat(result).isEqualTo(MarketStockIndicatorCommandService.IndicatorCollectionResult.SAVED);
        assertThat(permits).hasValue(2);
        verify(persistence).save(eq(stockId), org.mockito.ArgumentMatchers.argThat(command ->
                command.financialReferenceYearMonth().equals(YearMonth.of(2026, 6))));
    }

    @Test
    void missingFinancialDataSkipsPersistence() {
        var service = new MarketStockIndicatorCommandService(userClient, kisClient, persistence);
        when(kisClient.getQuote(credentials(), "005930")).thenReturn(quote());
        when(kisClient.getLatestQuarterlyFinancialRatio(credentials(), "005930")).thenReturn(Optional.empty());
        assertThat(service.collectAndSaveIndicatorsForIdentifiedStock(credentials(), UUID.randomUUID(), "005930", () -> true))
                .isEqualTo(MarketStockIndicatorCommandService.IndicatorCollectionResult.SKIPPED);
        verify(persistence, never()).save(any(), any());
    }

    @Test
    void missingPerAndPbrSkipsFinancialRatioRequest() {
        var service = new MarketStockIndicatorCommandService(userClient, kisClient, persistence);
        when(kisClient.getQuote(credentials(), "005930")).thenReturn(quoteWithoutValuation());

        assertThat(service.collectAndSaveIndicatorsForIdentifiedStock(
                credentials(), UUID.randomUUID(), "005930", () -> true))
                .isEqualTo(MarketStockIndicatorCommandService.IndicatorCollectionResult.SKIPPED);

        verify(kisClient, never()).getLatestQuarterlyFinancialRatio(any(), any());
        verify(persistence, never()).save(any(), any());
    }

    @Test
    void interruptBeforeSecondRequestStopsFinancialCallAndPersistence() {
        var service = new MarketStockIndicatorCommandService(userClient, kisClient, persistence);
        when(kisClient.getQuote(credentials(), "005930")).thenReturn(quote());
        AtomicInteger permits = new AtomicInteger();
        assertThat(service.collectAndSaveIndicatorsForIdentifiedStock(credentials(), UUID.randomUUID(), "005930",
                () -> permits.incrementAndGet() == 1))
                .isEqualTo(MarketStockIndicatorCommandService.IndicatorCollectionResult.INTERRUPTED);
        verify(kisClient, never()).getLatestQuarterlyFinancialRatio(any(), any());
        verify(persistence, never()).save(any(), any());
    }

    @Test
    void nullQuoteBecomesBusinessException() {
        var service = new MarketStockIndicatorCommandService(userClient, kisClient, persistence);
        when(kisClient.getQuote(credentials(), "005930")).thenReturn(null);
        assertThatThrownBy(() -> service.collectAndSaveIndicatorsForIdentifiedStock(
                credentials(), UUID.randomUUID(), "005930", () -> true))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MarketErrorCode.KIS_QUOTE_RESPONSE_INVALID));
    }

    private UserKisTokenResponse credentials() { return new UserKisTokenResponse("token", "key", "secret"); }
    private QuoteResponse quote() {
        return new QuoteResponse("005930", 70000L, null, null, null, null, null, null, null, null, null,
                new BigDecimal("15.2"), new BigDecimal("1.3"), null, null, null, Instant.parse("2026-09-10T01:00:00Z"));
    }
    private FinancialRatioResponse financial() {
        return new FinancialRatioResponse(YearMonth.of(2026, 6), new BigDecimal("31.39"), Instant.parse("2026-09-10T01:00:01Z"));
    }

    private QuoteResponse quoteWithoutValuation() {
        return new QuoteResponse("005930", 70000L, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, Instant.parse("2026-09-10T01:00:00Z"));
    }
}
