package com.sajo.market_service.market.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.market.client.kis.KisApiClient;
import com.sajo.market_service.market.client.user.UserAccountFeignClient;
import com.sajo.market_service.market.client.user.dto.UserKisTokenResponse;
import com.sajo.market_service.market.dto.response.QuoteResponse;
import com.sajo.market_service.market.exception.MarketErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketStockIndicatorCommandServiceTest {
    @Mock private UserAccountFeignClient userAccountFeignClient;
    @Mock private KisApiClient kisApiClient;
    @Mock private MarketStockIndicatorPersistenceService persistenceService;

    @Test
    void usesIdentifiedStockWithoutAnotherStockLookupAndSavesValidSnapshot() throws Exception {
        MarketStockIndicatorCommandService service = new MarketStockIndicatorCommandService(
                userAccountFeignClient, kisApiClient, persistenceService);
        UUID stockId = UUID.randomUUID();
        UserKisTokenResponse credentials = new UserKisTokenResponse("token", "key", "secret");
        QuoteResponse quote = new QuoteResponse("005930", 70_000L, null, null, null, null, null, null, null, null,
                null, new BigDecimal("15.2"), new BigDecimal("1.3"), null, null, null, LocalDate.of(2026, 9, 4));
        when(kisApiClient.getQuote(credentials, "005930")).thenReturn(quote);

        boolean saved = service.collectAndSaveIndicatorsForIdentifiedStock(credentials, stockId, "005930");

        assertThat(saved).isTrue();
        verify(persistenceService).save(org.mockito.ArgumentMatchers.eq(stockId), org.mockito.ArgumentMatchers.any());
        Method method = MarketStockIndicatorCommandService.class.getMethod(
                "collectAndSaveIndicatorsForIdentifiedStock", UserKisTokenResponse.class, UUID.class, String.class);
        assertThat(method.isAnnotationPresent(Transactional.class)).isFalse();
    }

    @Test
    void doesNotSaveWhenKisBusinessDateIsMissing() {
        MarketStockIndicatorCommandService service = new MarketStockIndicatorCommandService(
                userAccountFeignClient, kisApiClient, persistenceService);
        UserKisTokenResponse credentials = new UserKisTokenResponse("token", "key", "secret");
        when(kisApiClient.getQuote(credentials, "005930")).thenReturn(new QuoteResponse(
                "005930", 70_000L, null, null, null, null, null, null, null, null, null,
                new BigDecimal("15.2"), null, null, null, null, null));

        assertThat(service.collectAndSaveIndicatorsForIdentifiedStock(credentials, UUID.randomUUID(), "005930")).isFalse();
        verify(persistenceService, never()).save(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void convertsNullKisQuoteToExplicitBusinessExceptionWithoutLoggingNpe() {
        MarketStockIndicatorCommandService service = new MarketStockIndicatorCommandService(
                userAccountFeignClient, kisApiClient, persistenceService);
        UserKisTokenResponse credentials = new UserKisTokenResponse("token", "key", "secret");
        when(kisApiClient.getQuote(credentials, "005930")).thenReturn(null);

        assertThatThrownBy(() -> service.collectAndSaveIndicatorsForIdentifiedStock(
                credentials, UUID.randomUUID(), "005930"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MarketErrorCode.KIS_QUOTE_RESPONSE_INVALID));
        verify(persistenceService, never()).save(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
