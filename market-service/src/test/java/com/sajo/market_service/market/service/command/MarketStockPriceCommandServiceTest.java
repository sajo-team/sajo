package com.sajo.market_service.market.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.market.client.kis.KisApiClient;
import com.sajo.market_service.market.client.user.UserAccountFeignClient;
import com.sajo.market_service.market.client.user.dto.UserKisTokenResponse;
import com.sajo.market_service.market.domain.MarketStock;
import com.sajo.market_service.market.dto.response.DailyPriceResponse;
import com.sajo.market_service.market.exception.MarketErrorCode;
import com.sajo.market_service.market.repository.command.MarketStockCommandRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MarketStockPriceCommandServiceTest {

    private static final String STOCK_CODE = "005930";

    @Mock
    private MarketStockCommandRepository marketStockCommandRepository;
    @Mock
    private UserAccountFeignClient userAccountFeignClient;
    @Mock
    private KisApiClient kisApiClient;
    @Mock
    private MarketStockPriceDailyPersistenceService persistenceService;
    @Mock
    private MarketStock marketStock;

    private MarketStockPriceCommandService service;
    private UUID userId;
    private UUID stockId;
    private UserKisTokenResponse credentials;

    @BeforeEach
    void setUp() {
        service = new MarketStockPriceCommandService(
                marketStockCommandRepository, userAccountFeignClient, kisApiClient, persistenceService);
        userId = UUID.randomUUID();
        stockId = UUID.randomUUID();
        credentials = new UserKisTokenResponse("access-token", "app-key", "secret-key");
        org.mockito.Mockito.lenient().when(marketStock.getId()).thenReturn(stockId);
    }

    @Test
    @DisplayName("외부 호출 완료 후 DB 저장 전용 Service에 일별 시세를 전달한다")
    void fetchesOutsidePersistenceTransactionThenSaves() {
        LocalDate tradeDate = LocalDate.of(2026, 9, 1);
        List<DailyPriceResponse> prices = List.of(dailyPrice(tradeDate));
        givenStockExists();
        given(userAccountFeignClient.getKisToken(userId)).willReturn(credentials);
        given(kisApiClient.getDailyPrices(credentials, STOCK_CODE, tradeDate, tradeDate)).willReturn(prices);
        given(persistenceService.saveDailyPrices(stockId, tradeDate, tradeDate, prices)).willReturn(1);

        int saved = service.collectAndSaveDailyPrices(userId, STOCK_CODE, tradeDate, tradeDate);

        assertThat(saved).isEqualTo(1);
        InOrder order = inOrder(userAccountFeignClient, kisApiClient, persistenceService);
        order.verify(userAccountFeignClient).getKisToken(userId);
        order.verify(kisApiClient).getDailyPrices(credentials, STOCK_CODE, tradeDate, tradeDate);
        order.verify(persistenceService).saveDailyPrices(stockId, tradeDate, tradeDate, prices);
    }

    @Test
    @DisplayName("외부 API 호출 Service에는 Transaction을 두지 않고 저장 Service에만 Transaction을 둔다")
    void separatesExternalCallsFromPersistenceTransaction() throws Exception {
        Method collectionMethod = MarketStockPriceCommandService.class.getMethod(
                "collectAndSaveDailyPrices", UUID.class, String.class, LocalDate.class, LocalDate.class);
        Method persistenceMethod = MarketStockPriceDailyPersistenceService.class.getMethod(
                "saveDailyPrices", UUID.class, LocalDate.class, LocalDate.class, List.class);

        assertThat(collectionMethod.isAnnotationPresent(Transactional.class)).isFalse();
        assertThat(persistenceMethod.getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    @DisplayName("존재하지 않는 종목이면 외부 API와 DB 저장을 호출하지 않는다")
    void doesNotFetchOrSaveWhenStockDoesNotExist() {
        LocalDate tradeDate = LocalDate.of(2026, 9, 1);
        given(marketStockCommandRepository.findByStockCode(STOCK_CODE)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.collectAndSaveDailyPrices(userId, STOCK_CODE, tradeDate, tradeDate))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(MarketErrorCode.INVALID_MARKET_STOCK));

        verify(userAccountFeignClient, never()).getKisToken(any());
        verify(kisApiClient, never()).getDailyPrices(any(), any(), any(), any());
        verify(persistenceService, never()).saveDailyPrices(any(), any(), any(), any());
    }

    @Test
    @DisplayName("잘못된 날짜 범위이면 외부 API와 DB 저장을 호출하지 않는다")
    void doesNotFetchOrSaveWhenDateRangeIsInvalid() {
        assertThatThrownBy(() -> service.collectAndSaveDailyPrices(
                userId, STOCK_CODE, LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 1)))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(MarketErrorCode.INVALID_MARKET_STOCK_PRICE));

        verify(marketStockCommandRepository, never()).findByStockCode(any());
        verify(kisApiClient, never()).getDailyPrices(any(), any(), any(), any());
        verify(persistenceService, never()).saveDailyPrices(any(), any(), any(), any());
    }

    @Test
    @DisplayName("KIS BusinessException은 저장하지 않고 그대로 전파한다")
    void doesNotSaveAndPropagatesKisBusinessException() {
        LocalDate tradeDate = LocalDate.of(2026, 9, 1);
        BusinessException exception = new BusinessException(
                MarketErrorCode.KIS_QUOTE_RESPONSE_INVALID, "KIS 일별 시세 조회에 실패했습니다.");
        givenStockExists();
        given(userAccountFeignClient.getKisToken(userId)).willReturn(credentials);
        given(kisApiClient.getDailyPrices(credentials, STOCK_CODE, tradeDate, tradeDate)).willThrow(exception);

        assertThatThrownBy(() -> service.collectAndSaveDailyPrices(userId, STOCK_CODE, tradeDate, tradeDate))
                .isSameAs(exception);

        verify(persistenceService, never()).saveDailyPrices(any(), any(), any(), any());
    }

    @Test
    @DisplayName("DB 저장 실패는 호출자에게 전파한다")
    void propagatesDatabaseSaveFailure() {
        LocalDate tradeDate = LocalDate.of(2026, 9, 1);
        List<DailyPriceResponse> prices = List.of(dailyPrice(tradeDate));
        DataAccessResourceFailureException exception = new DataAccessResourceFailureException("database unavailable");
        givenStockExists();
        given(userAccountFeignClient.getKisToken(userId)).willReturn(credentials);
        given(kisApiClient.getDailyPrices(credentials, STOCK_CODE, tradeDate, tradeDate)).willReturn(prices);
        given(persistenceService.saveDailyPrices(stockId, tradeDate, tradeDate, prices)).willThrow(exception);

        assertThatThrownBy(() -> service.collectAndSaveDailyPrices(userId, STOCK_CODE, tradeDate, tradeDate))
                .isSameAs(exception);
    }

    private void givenStockExists() {
        given(marketStockCommandRepository.findByStockCode(STOCK_CODE)).willReturn(Optional.of(marketStock));
    }

    private DailyPriceResponse dailyPrice(LocalDate tradeDate) {
        return new DailyPriceResponse(tradeDate, 69_000L, 70_500L, 68_800L, 70_000L, 123_456L, 8_610_000_000L);
    }
}
