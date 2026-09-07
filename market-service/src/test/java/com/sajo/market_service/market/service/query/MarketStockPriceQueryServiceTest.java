package com.sajo.market_service.market.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.market.domain.MarketStock;
import com.sajo.market_service.market.domain.MarketStockPrice;
import com.sajo.market_service.market.domain.PriceSource;
import com.sajo.market_service.market.dto.response.MarketStockPriceResponse;
import com.sajo.market_service.market.exception.MarketErrorCode;
import com.sajo.market_service.market.repository.query.MarketStockPriceQueryRepository;
import com.sajo.market_service.market.repository.query.MarketStockQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MarketStockPriceQueryServiceTest {

    @Mock
    private MarketStockQueryRepository marketStockQueryRepository;

    @Mock
    private MarketStockPriceQueryRepository marketStockPriceQueryRepository;

    private MarketStockPriceQueryService service;
    private final UUID stockId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new MarketStockPriceQueryService(marketStockQueryRepository, marketStockPriceQueryRepository);
    }

    @Test
    void returnsRecentRestDailyPricesInAscendingTradeDateOrder() {
        given(marketStockQueryRepository.findByStockCode("005930")).willReturn(Optional.of(stock()));
        given(marketStockPriceQueryRepository.findRecentDailyRestPrices(
                org.mockito.ArgumentMatchers.eq(stockId), org.mockito.ArgumentMatchers.eq(PriceSource.REST), org.mockito.ArgumentMatchers.any()))
                .willReturn(List.of(price(LocalDate.of(2026, 9, 3)), price(LocalDate.of(2026, 9, 1))));

        List<MarketStockPriceResponse> response = service.getRecentDailyPrices("005930", 30);

        assertThat(response).extracting(MarketStockPriceResponse::tradeDate)
                .containsExactly(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3));
        assertThat(response.getFirst().closePrice()).isEqualTo(70_000L);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(marketStockPriceQueryRepository).findRecentDailyRestPrices(
                org.mockito.ArgumentMatchers.eq(stockId), org.mockito.ArgumentMatchers.eq(PriceSource.REST), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(30);
    }

    @Test
    void returnsEmptyListWhenStockHasNoSavedDailyPrices() {
        given(marketStockQueryRepository.findByStockCode("005930")).willReturn(Optional.of(stock()));
        given(marketStockPriceQueryRepository.findRecentDailyRestPrices(
                org.mockito.ArgumentMatchers.eq(stockId), org.mockito.ArgumentMatchers.eq(PriceSource.REST), org.mockito.ArgumentMatchers.any()))
                .willReturn(List.of());

        assertThat(service.getRecentDailyPrices("005930", 30)).isEmpty();
    }

    @Test
    void rejectsInvalidStockCodeAndDays() {
        assertThatThrownBy(() -> service.getRecentDailyPrices("123", 30)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.getRecentDailyPrices("005930", 0))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(MarketErrorCode.INVALID_MARKET_STOCK_PRICE));
        assertThatThrownBy(() -> service.getRecentDailyPrices("005930", 366))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void throwsNotFoundWhenValidStockCodeDoesNotExist() {
        given(marketStockQueryRepository.findByStockCode("999999")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRecentDailyPrices("999999", 30))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(MarketErrorCode.MARKET_STOCK_NOT_FOUND));
    }

    private MarketStock stock() {
        MarketStock stock = MarketStock.create("005930", "삼성전자", "KOSPI", null, null, null);
        ReflectionTestUtils.setField(stock, "id", stockId);
        return stock;
    }

    private MarketStockPrice price(LocalDate date) {
        return MarketStockPrice.create(
                stockId, date, null, null, 70_000L, 69_000L, 71_000L, 68_000L,
                null, null, null, null, 123_456L, 8_610_000_000L, null, PriceSource.REST);
    }
}
