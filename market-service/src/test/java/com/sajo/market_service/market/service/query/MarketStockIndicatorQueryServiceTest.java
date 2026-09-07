package com.sajo.market_service.market.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.market.domain.MarketStock;
import com.sajo.market_service.market.domain.MarketStockIndicator;
import com.sajo.market_service.market.dto.response.MarketStockIndicatorResponse;
import com.sajo.market_service.market.exception.MarketErrorCode;
import com.sajo.market_service.market.repository.query.MarketStockIndicatorQueryRepository;
import com.sajo.market_service.market.repository.query.MarketStockQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
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
                new BigDecimal("1.23"), new BigDecimal("1000"), new BigDecimal("20000"), new BigDecimal("8.76"));
    }
}
