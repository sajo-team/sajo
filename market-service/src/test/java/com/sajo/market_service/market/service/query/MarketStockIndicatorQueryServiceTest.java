package com.sajo.market_service.market.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.market.domain.FinancialPeriodType;
import com.sajo.market_service.market.domain.MarketStock;
import com.sajo.market_service.market.domain.MarketStockIndicator;
import com.sajo.market_service.market.dto.response.MarketStockIndicatorResponse;
import com.sajo.market_service.market.exception.MarketErrorCode;
import com.sajo.market_service.market.repository.query.MarketStockIndicatorQueryRepository;
import com.sajo.market_service.market.repository.query.MarketStockQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
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
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MarketStockIndicatorQueryServiceTest {

    @Mock
    private MarketStockQueryRepository marketStockQueryRepository;

    @Mock
    private MarketStockIndicatorQueryRepository marketStockIndicatorQueryRepository;

    private MarketStockIndicatorQueryService service;
    private final UUID stockId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new MarketStockIndicatorQueryService(marketStockQueryRepository, marketStockIndicatorQueryRepository);
    }

    @Test
    void returnsLatestIndicatorSelectedByRepositoryReferenceDateOrder() {
        given(marketStockQueryRepository.findByStockCode("005930")).willReturn(Optional.of(stock()));
        given(marketStockIndicatorQueryRepository.findTopByStockIdOrderByReferenceDateDescCreatedAtDesc(stockId))
                .willReturn(Optional.of(indicator()));

        MarketStockIndicatorResponse response = service.getLatestIndicator("005930");

        assertThat(response.referenceDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(response.per()).isEqualByComparingTo("12.34");
        verify(marketStockIndicatorQueryRepository).findTopByStockIdOrderByReferenceDateDescCreatedAtDesc(stockId);
    }

    @Test
    void distinguishesMissingStockAndMissingIndicator() {
        given(marketStockQueryRepository.findByStockCode("999999")).willReturn(Optional.empty());
        assertErrorCode("999999", MarketErrorCode.MARKET_STOCK_NOT_FOUND);

        given(marketStockQueryRepository.findByStockCode("005930")).willReturn(Optional.of(stock()));
        given(marketStockIndicatorQueryRepository.findTopByStockIdOrderByReferenceDateDescCreatedAtDesc(stockId))
                .willReturn(Optional.empty());
        assertErrorCode("005930", MarketErrorCode.MARKET_STOCK_INDICATOR_NOT_FOUND);
    }

    @Test
    void returnsLegacyIndicatorWhenFinancialSnapshotIsNotYetCollected() {
        given(marketStockQueryRepository.findByStockCode("005930")).willReturn(Optional.of(stock()));
        given(marketStockIndicatorQueryRepository
                .findTopByStockIdAndFinancialPeriodTypeIsNotNullAndFinancialReferenceYearMonthIsNotNullOrderByFinancialReferenceYearMonthDescFinancialFetchedAtDesc(stockId))
                .willReturn(Optional.empty());
        given(marketStockIndicatorQueryRepository.findTopByStockIdOrderByReferenceDateDescCreatedAtDesc(stockId))
                .willReturn(Optional.of(indicator()));

        var snapshot = service.getLatestFinancialIndicator("005930");

        assertThat(snapshot.per()).isEqualByComparingTo("12.34");
        assertThat(snapshot.pbr()).isEqualByComparingTo("1.23");
        assertThat(snapshot.financialReferenceYearMonth()).isNull();
    }

    @Test
    void returnsFinancialHistoryOrderedByReferenceYearMonthDescAndSkipsLegacyLookup() {
        given(marketStockQueryRepository.findByStockCode("005930")).willReturn(Optional.of(stock()));
        MarketStockIndicator newer = financialIndicator("2026-06", Instant.parse("2026-09-10T01:00:01Z"));
        MarketStockIndicator older = financialIndicator("2026-03", Instant.parse("2026-06-10T01:00:01Z"));
        given(marketStockIndicatorQueryRepository
                .findByStockIdAndFinancialPeriodTypeIsNotNullAndFinancialReferenceYearMonthIsNotNullOrderByFinancialReferenceYearMonthDescFinancialFetchedAtDesc(
                        eq(stockId), any()))
                .willReturn(List.of(newer, older));

        List<MarketStockIndicatorResponse> history = service.getIndicatorHistory("005930", 8);

        assertThat(history).extracting(MarketStockIndicatorResponse::financialReferenceYearMonth)
                .containsExactly("2026-06", "2026-03");
        verify(marketStockIndicatorQueryRepository, never())
                .findByStockIdAndReferenceDateIsNotNullOrderByReferenceDateDescCreatedAtDesc(any(), any());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(marketStockIndicatorQueryRepository).findByStockIdAndFinancialPeriodTypeIsNotNullAndFinancialReferenceYearMonthIsNotNullOrderByFinancialReferenceYearMonthDescFinancialFetchedAtDesc(
                eq(stockId), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(8);
    }

    @Test
    void fallsBackToLegacyHistoryWhenNoFinancialSnapshotExists() {
        given(marketStockQueryRepository.findByStockCode("005930")).willReturn(Optional.of(stock()));
        given(marketStockIndicatorQueryRepository
                .findByStockIdAndFinancialPeriodTypeIsNotNullAndFinancialReferenceYearMonthIsNotNullOrderByFinancialReferenceYearMonthDescFinancialFetchedAtDesc(
                        eq(stockId), any()))
                .willReturn(List.of());
        given(marketStockIndicatorQueryRepository
                .findByStockIdAndReferenceDateIsNotNullOrderByReferenceDateDescCreatedAtDesc(
                        eq(stockId), any()))
                .willReturn(List.of(indicator()));

        List<MarketStockIndicatorResponse> history = service.getIndicatorHistory("005930", 8);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).referenceDate()).isEqualTo(LocalDate.of(2026, 9, 1));
    }

    @Test
    void returnsEmptyHistoryWhenStockHasNoIndicatorAtAll() {
        given(marketStockQueryRepository.findByStockCode("005930")).willReturn(Optional.of(stock()));
        given(marketStockIndicatorQueryRepository
                .findByStockIdAndFinancialPeriodTypeIsNotNullAndFinancialReferenceYearMonthIsNotNullOrderByFinancialReferenceYearMonthDescFinancialFetchedAtDesc(
                        eq(stockId), any()))
                .willReturn(List.of());
        given(marketStockIndicatorQueryRepository
                .findByStockIdAndReferenceDateIsNotNullOrderByReferenceDateDescCreatedAtDesc(
                        eq(stockId), any()))
                .willReturn(List.of());

        List<MarketStockIndicatorResponse> history = service.getIndicatorHistory("005930", 8);

        assertThat(history).isEmpty();
    }

    @Test
    void throwsStockNotFoundForHistoryOfMissingStock() {
        given(marketStockQueryRepository.findByStockCode("999999")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getIndicatorHistory("999999", 8))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(MarketErrorCode.MARKET_STOCK_NOT_FOUND));
    }

    @Test
    void rejectsOutOfRangeHistoryLimit() {
        assertThatThrownBy(() -> service.getIndicatorHistory("005930", 0))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(MarketErrorCode.INVALID_MARKET_STOCK_INDICATOR));

        assertThatThrownBy(() -> service.getIndicatorHistory("005930", 41))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(MarketErrorCode.INVALID_MARKET_STOCK_INDICATOR));
    }

    private void assertErrorCode(String stockCode, MarketErrorCode errorCode) {
        assertThatThrownBy(() -> service.getLatestIndicator(stockCode))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode()).isEqualTo(errorCode));
    }

    private MarketStock stock() {
        MarketStock stock = MarketStock.create("005930", "삼성전자", "KOSPI", null, null, null);
        ReflectionTestUtils.setField(stock, "id", stockId);
        return stock;
    }

    private MarketStockIndicator indicator() {
        return MarketStockIndicator.create(stockId, LocalDate.of(2026, 9, 1), new BigDecimal("12.34"),
                new BigDecimal("1.23"), new BigDecimal("8.76"));
    }

    /**
     * 신규 분기 스냅샷은 JDBC upsert로만 채워지고 엔티티에 공개 생성자가 없어,
     * 테스트에서는 레거시 팩토리로 만든 뒤 리플렉션으로 분기 필드를 채운다.
     */
    private MarketStockIndicator financialIndicator(String yearMonth, Instant fetchedAt) {
        MarketStockIndicator indicator = MarketStockIndicator.create(stockId, LocalDate.of(2026, 1, 1),
                new BigDecimal("10.0"), new BigDecimal("1.0"), new BigDecimal("5.0"));
        ReflectionTestUtils.setField(indicator, "referenceDate", null);
        ReflectionTestUtils.setField(indicator, "financialPeriodType", FinancialPeriodType.QUARTER);
        ReflectionTestUtils.setField(indicator, "financialReferenceYearMonth", yearMonth);
        ReflectionTestUtils.setField(indicator, "financialFetchedAt", fetchedAt);
        return indicator;
    }
}
