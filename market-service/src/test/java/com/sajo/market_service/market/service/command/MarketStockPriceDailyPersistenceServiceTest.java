package com.sajo.market_service.market.service.command;

import com.sajo.market_service.market.domain.PriceSource;
import com.sajo.market_service.market.dto.response.DailyPriceResponse;
import com.sajo.market_service.market.repository.command.MarketStockPriceCommandRepository;
import com.sajo.market_service.market.repository.command.MarketStockPriceDailyRestWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MarketStockPriceDailyPersistenceServiceTest {

    @Mock
    private MarketStockPriceCommandRepository marketStockPriceCommandRepository;
    @Mock
    private MarketStockPriceDailyRestWriter marketStockPriceDailyRestWriter;

    private MarketStockPriceDailyPersistenceService service;
    private UUID stockId;
    private LocalDate startDate;
    private LocalDate endDate;

    @BeforeEach
    void setUp() {
        service = new MarketStockPriceDailyPersistenceService(
                marketStockPriceCommandRepository, marketStockPriceDailyRestWriter);
        stockId = UUID.randomUUID();
        startDate = LocalDate.of(2026, 9, 1);
        endDate = LocalDate.of(2026, 9, 3);
    }

    @Test
    @DisplayName("기존 REST 일별 거래일은 한 번만 조회하고 새 거래일만 일괄 저장한다")
    void fetchesExistingDatesOnceAndWritesOnlyNewPrices() {
        DailyPriceResponse existing = dailyPrice(startDate);
        DailyPriceResponse newPrice = dailyPrice(startDate.plusDays(1));
        givenExistingDates(Set.of(existing.tradeDate()));
        given(marketStockPriceDailyRestWriter.insertIgnoringDuplicates(stockId, List.of(newPrice))).willReturn(1);

        int saved = service.saveDailyPrices(stockId, startDate, endDate, List.of(existing, newPrice));

        assertThat(saved).isEqualTo(1);
        verify(marketStockPriceCommandRepository).findDatesByStockIdAndDateBetweenAndTimeIsNullAndSource(
                stockId, startDate, endDate, PriceSource.REST);
        verify(marketStockPriceDailyRestWriter).insertIgnoringDuplicates(stockId, List.of(newPrice));
    }

    @Test
    @DisplayName("같은 KIS 응답에 중복 거래일이 있어도 한 행만 저장한다")
    void removesDuplicateDatesFromIncomingPrices() {
        DailyPriceResponse price = dailyPrice(startDate);
        givenExistingDates(Set.of());
        given(marketStockPriceDailyRestWriter.insertIgnoringDuplicates(stockId, List.of(price))).willReturn(1);

        int saved = service.saveDailyPrices(stockId, startDate, endDate, List.of(price, price));

        assertThat(saved).isEqualTo(1);
        verify(marketStockPriceDailyRestWriter).insertIgnoringDuplicates(stockId, List.of(price));
    }

    @Test
    @DisplayName("WEBSOCKET 행은 REST 조건의 기존 날짜 조회에 포함되지 않아 저장을 막지 않는다")
    void websocketRowsDoNotBlockRestDailyPriceInsert() {
        DailyPriceResponse price = dailyPrice(startDate);
        givenExistingDates(Set.of());
        given(marketStockPriceDailyRestWriter.insertIgnoringDuplicates(stockId, List.of(price))).willReturn(1);

        int saved = service.saveDailyPrices(stockId, startDate, endDate, List.of(price));

        assertThat(saved).isEqualTo(1);
        verify(marketStockPriceCommandRepository).findDatesByStockIdAndDateBetweenAndTimeIsNullAndSource(
                stockId, startDate, endDate, PriceSource.REST);
    }

    @Test
    @DisplayName("동시 저장으로 ON CONFLICT가 중복 행을 무시해도 정상 종료한다")
    void completesNormallyWhenConcurrentInsertIsIgnored() {
        DailyPriceResponse price = dailyPrice(startDate);
        givenExistingDates(Set.of());
        given(marketStockPriceDailyRestWriter.insertIgnoringDuplicates(stockId, List.of(price))).willReturn(0);

        int saved = service.saveDailyPrices(stockId, startDate, endDate, List.of(price));

        assertThat(saved).isZero();
    }

    @Test
    @DisplayName("같은 데이터를 재실행하면 writer를 호출하지 않는다")
    void doesNotWriteWhenAllDatesAlreadyExist() {
        DailyPriceResponse price = dailyPrice(startDate);
        givenExistingDates(Set.of(startDate));

        int saved = service.saveDailyPrices(stockId, startDate, endDate, List.of(price));

        assertThat(saved).isZero();
        verify(marketStockPriceDailyRestWriter, never()).insertIgnoringDuplicates(any(), any());
    }

    @Test
    @DisplayName("DB batch 저장 실패는 호출자에게 전파한다")
    void propagatesBatchWriteFailure() {
        DailyPriceResponse price = dailyPrice(startDate);
        DataAccessResourceFailureException exception = new DataAccessResourceFailureException("database unavailable");
        givenExistingDates(Set.of());
        given(marketStockPriceDailyRestWriter.insertIgnoringDuplicates(eq(stockId), any())).willThrow(exception);

        assertThatThrownBy(() -> service.saveDailyPrices(stockId, startDate, endDate, List.of(price)))
                .isSameAs(exception);
    }

    private void givenExistingDates(Set<LocalDate> dates) {
        given(marketStockPriceCommandRepository.findDatesByStockIdAndDateBetweenAndTimeIsNullAndSource(
                stockId, startDate, endDate, PriceSource.REST)).willReturn(dates);
    }

    private DailyPriceResponse dailyPrice(LocalDate date) {
        return new DailyPriceResponse(date, 69_000L, 70_500L, 68_800L, 70_000L, 123_456L, 8_610_000_000L);
    }
}
