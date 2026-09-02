package com.sajo.market_service.market.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.market.client.kis.KisApiClient;
import com.sajo.market_service.market.client.user.UserAccountFeignClient;
import com.sajo.market_service.market.client.user.dto.UserKisTokenResponse;
import com.sajo.market_service.market.domain.MarketStock;
import com.sajo.market_service.market.domain.MarketStockPrice;
import com.sajo.market_service.market.domain.PriceSource;
import com.sajo.market_service.market.dto.response.DailyPriceResponse;
import com.sajo.market_service.market.exception.MarketErrorCode;
import com.sajo.market_service.market.repository.command.MarketStockCommandRepository;
import com.sajo.market_service.market.repository.command.MarketStockPriceCommandRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class MarketStockPriceCommandServiceTest {

    private static final String STOCK_CODE = "005930";

    @Mock
    private MarketStockCommandRepository marketStockCommandRepository;

    @Mock
    private MarketStockPriceCommandRepository marketStockPriceCommandRepository;

    @Mock
    private UserAccountFeignClient userAccountFeignClient;

    @Mock
    private KisApiClient kisApiClient;

    @Mock
    private MarketStock marketStock;

    private MarketStockPriceCommandService service;
    private UUID userId;
    private UUID stockId;
    private UserKisTokenResponse credentials;

    @BeforeEach
    void setUp() {
        service = new MarketStockPriceCommandService(
                marketStockCommandRepository,
                marketStockPriceCommandRepository,
                userAccountFeignClient,
                kisApiClient
        );
        userId = UUID.randomUUID();
        stockId = UUID.randomUUID();
        credentials = new UserKisTokenResponse("access-token", "app-key", "secret-key");
        lenient().when(marketStock.getId()).thenReturn(stockId);
    }

    @Test
    @DisplayName("KIS 일별 시세를 REST 일별 데이터로 저장한다")
    void savesDailyPrice() {
        DailyPriceResponse price = dailyPrice(LocalDate.of(2026, 9, 1), 70_000L);
        givenStockExists();
        given(userAccountFeignClient.getKisToken(userId)).willReturn(credentials);
        given(kisApiClient.getDailyPrices(credentials, STOCK_CODE, price.tradeDate(), price.tradeDate()))
                .willReturn(List.of(price));
        given(marketStockPriceCommandRepository.existsByStockIdAndDateAndTimeIsNullAndSource(
                stockId, price.tradeDate(), PriceSource.REST)).willReturn(false);

        int saved = service.collectAndSaveDailyPrices(userId, STOCK_CODE, price.tradeDate(), price.tradeDate());

        ArgumentCaptor<MarketStockPrice> captor = ArgumentCaptor.forClass(MarketStockPrice.class);
        verify(marketStockPriceCommandRepository).save(captor.capture());
        MarketStockPrice savedPrice = captor.getValue();
        assertThat(saved).isEqualTo(1);
        assertThat(savedPrice.getStockId()).isEqualTo(stockId);
        assertThat(savedPrice.getDate()).isEqualTo(price.tradeDate());
        assertThat(savedPrice.getTime()).isNull();
        assertThat(savedPrice.getSource()).isEqualTo(PriceSource.REST);
        assertThat(savedPrice.getCurrentPrice()).isNull();
        assertThat(savedPrice.getClosePrice()).isEqualTo(70_000L);
        assertThat(savedPrice.getOpenPrice()).isEqualTo(69_000L);
        assertThat(savedPrice.getHighPrice()).isEqualTo(70_500L);
        assertThat(savedPrice.getLowPrice()).isEqualTo(68_800L);
        assertThat(savedPrice.getVolume()).isNull();
        assertThat(savedPrice.getAccumulatedVolume()).isEqualTo(123_456L);
        assertThat(savedPrice.getAccumulatedTradeAmount()).isEqualTo(8_610_000_000L);
    }

    @Test
    @DisplayName("stockCode로 MarketStock을 조회한 뒤 여러 거래일을 저장한다")
    void findsStockByStockCodeAndSavesMultipleDates() {
        LocalDate startDate = LocalDate.of(2026, 9, 1);
        LocalDate endDate = LocalDate.of(2026, 9, 2);
        givenStockExists();
        given(userAccountFeignClient.getKisToken(userId)).willReturn(credentials);
        given(kisApiClient.getDailyPrices(credentials, STOCK_CODE, startDate, endDate))
                .willReturn(List.of(dailyPrice(startDate, 70_000L), dailyPrice(endDate, 71_000L)));
        given(marketStockPriceCommandRepository.existsByStockIdAndDateAndTimeIsNullAndSource(
                eq(stockId), any(LocalDate.class), eq(PriceSource.REST))).willReturn(false);

        int saved = service.collectAndSaveDailyPrices(userId, STOCK_CODE, startDate, endDate);

        assertThat(saved).isEqualTo(2);
        verify(marketStockCommandRepository).findByStockCode(STOCK_CODE);
        verify(marketStockPriceCommandRepository, times(2)).save(any(MarketStockPrice.class));
    }

    @Test
    @DisplayName("같은 종목과 거래일의 REST 일별 데이터는 다시 저장하지 않는다")
    void doesNotSaveDuplicatedRestDailyPrice() {
        LocalDate tradeDate = LocalDate.of(2026, 9, 1);
        givenStockExists();
        given(userAccountFeignClient.getKisToken(userId)).willReturn(credentials);
        given(kisApiClient.getDailyPrices(credentials, STOCK_CODE, tradeDate, tradeDate))
                .willReturn(List.of(dailyPrice(tradeDate, 70_000L)));
        given(marketStockPriceCommandRepository.existsByStockIdAndDateAndTimeIsNullAndSource(
                stockId, tradeDate, PriceSource.REST)).willReturn(true);

        int saved = service.collectAndSaveDailyPrices(userId, STOCK_CODE, tradeDate, tradeDate);

        assertThat(saved).isZero();
        verify(marketStockPriceCommandRepository, never()).save(any(MarketStockPrice.class));
    }

    @Test
    @DisplayName("같은 날짜의 WEBSOCKET 데이터가 있어도 REST 일별 데이터는 저장한다")
    void savesRestDailyPriceWhenWebsocketPriceExistsForSameDate() {
        LocalDate tradeDate = LocalDate.of(2026, 9, 1);
        givenStockExists();
        given(userAccountFeignClient.getKisToken(userId)).willReturn(credentials);
        given(kisApiClient.getDailyPrices(credentials, STOCK_CODE, tradeDate, tradeDate))
                .willReturn(List.of(dailyPrice(tradeDate, 70_000L)));
        given(marketStockPriceCommandRepository.existsByStockIdAndDateAndTimeIsNullAndSource(
                stockId, tradeDate, PriceSource.REST)).willReturn(false);

        int saved = service.collectAndSaveDailyPrices(userId, STOCK_CODE, tradeDate, tradeDate);

        assertThat(saved).isEqualTo(1);
        verify(marketStockPriceCommandRepository).existsByStockIdAndDateAndTimeIsNullAndSource(
                stockId, tradeDate, PriceSource.REST);
        verify(marketStockPriceCommandRepository).save(any(MarketStockPrice.class));
    }

    @Test
    @DisplayName("존재하지 않는 종목이면 KIS 조회와 DB 저장을 하지 않는다")
    void throwsWhenStockDoesNotExist() {
        LocalDate tradeDate = LocalDate.of(2026, 9, 1);
        given(marketStockCommandRepository.findByStockCode(STOCK_CODE)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.collectAndSaveDailyPrices(userId, STOCK_CODE, tradeDate, tradeDate))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(MarketErrorCode.INVALID_MARKET_STOCK));

        verify(userAccountFeignClient, never()).getKisToken(any(UUID.class));
        verify(kisApiClient, never()).getDailyPrices(any(), any(), any(), any());
        verify(marketStockPriceCommandRepository, never()).save(any(MarketStockPrice.class));
    }

    @Test
    @DisplayName("잘못된 날짜 범위이면 외부 조회와 DB 저장을 하지 않는다")
    void throwsWhenDateRangeIsInvalid() {
        LocalDate startDate = LocalDate.of(2026, 9, 2);
        LocalDate endDate = LocalDate.of(2026, 9, 1);

        assertThatThrownBy(() -> service.collectAndSaveDailyPrices(userId, STOCK_CODE, startDate, endDate))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(MarketErrorCode.INVALID_MARKET_STOCK_PRICE));

        verify(marketStockCommandRepository, never()).findByStockCode(any());
        verify(kisApiClient, never()).getDailyPrices(any(), any(), any(), any());
        verify(marketStockPriceCommandRepository, never()).save(any(MarketStockPrice.class));
    }

    @Test
    @DisplayName("KIS 조회가 실패하면 DB 저장을 하지 않고 BusinessException을 전파한다")
    void doesNotSaveAndPropagatesBusinessExceptionWhenKisLookupFails() {
        LocalDate tradeDate = LocalDate.of(2026, 9, 1);
        BusinessException exception = new BusinessException(
                MarketErrorCode.KIS_QUOTE_RESPONSE_INVALID, "KIS 일별 시세 조회에 실패했습니다.");
        givenStockExists();
        given(userAccountFeignClient.getKisToken(userId)).willReturn(credentials);
        given(kisApiClient.getDailyPrices(credentials, STOCK_CODE, tradeDate, tradeDate)).willThrow(exception);

        assertThatThrownBy(() -> service.collectAndSaveDailyPrices(userId, STOCK_CODE, tradeDate, tradeDate))
                .isSameAs(exception);

        verify(marketStockPriceCommandRepository, never()).save(any(MarketStockPrice.class));
    }

    @Test
    @DisplayName("DB 저장 실패는 그대로 호출자에게 전파한다")
    void propagatesDatabaseSaveFailure() {
        LocalDate tradeDate = LocalDate.of(2026, 9, 1);
        DataIntegrityViolationException exception = new DataIntegrityViolationException("duplicate key");
        givenStockExists();
        given(userAccountFeignClient.getKisToken(userId)).willReturn(credentials);
        given(kisApiClient.getDailyPrices(credentials, STOCK_CODE, tradeDate, tradeDate))
                .willReturn(List.of(dailyPrice(tradeDate, 70_000L)));
        given(marketStockPriceCommandRepository.existsByStockIdAndDateAndTimeIsNullAndSource(
                stockId, tradeDate, PriceSource.REST)).willReturn(false);
        given(marketStockPriceCommandRepository.save(any(MarketStockPrice.class))).willThrow(exception);

        assertThatThrownBy(() -> service.collectAndSaveDailyPrices(userId, STOCK_CODE, tradeDate, tradeDate))
                .isSameAs(exception);
    }

    private void givenStockExists() {
        given(marketStockCommandRepository.findByStockCode(STOCK_CODE)).willReturn(Optional.of(marketStock));
    }

    private DailyPriceResponse dailyPrice(LocalDate tradeDate, Long closePrice) {
        return new DailyPriceResponse(
                tradeDate, 69_000L, 70_500L, 68_800L, closePrice, 123_456L, 8_610_000_000L);
    }
}
