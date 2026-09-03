package com.sajo.market_service.market.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.common.response.PageResponse;
import com.sajo.market_service.market.domain.MarketStock;
import com.sajo.market_service.market.dto.response.MarketStockResponse;
import com.sajo.market_service.market.exception.MarketErrorCode;
import com.sajo.market_service.market.repository.query.MarketStockQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MarketStockQueryServiceTest {

    @Mock
    private MarketStockQueryRepository marketStockQueryRepository;

    private MarketStockQueryService service;

    @BeforeEach
    void setUp() {
        service = new MarketStockQueryService(marketStockQueryRepository);
    }

    @Test
    void getsStocksWithMarketTypeFilterAndDefaultStockCodeSort() {
        MarketStock stock = stock("005930", "삼성전자", "KOSPI");
        given(marketStockQueryRepository.findByMarketType(org.mockito.ArgumentMatchers.eq("KOSPI"), org.mockito.ArgumentMatchers.any()))
                .willReturn(new PageImpl<>(List.of(stock)));

        PageResponse<MarketStockResponse> response = service.getStocks(" KOSPI ", 0, 10, null);

        assertThat(response.content()).extracting(MarketStockResponse::stockCode).containsExactly("005930");
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(marketStockQueryRepository).findByMarketType(org.mockito.ArgumentMatchers.eq("KOSPI"), captor.capture());
        assertThat(captor.getValue().getSort().getOrderFor("stockCode").getDirection().isAscending()).isTrue();
    }

    @Test
    void escapesLikeWildcardsForStockNameAndCodeSearch() {
        given(marketStockQueryRepository.searchByStockNameOrStockCode(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .willReturn(new PageImpl<>(List.of()));

        service.searchStocks(" %_! ", 0, 10, "stockName,desc");

        verify(marketStockQueryRepository).searchByStockNameOrStockCode(org.mockito.ArgumentMatchers.eq("!%!_!!"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void preservesOrdinaryKoreanNameAndPartialStockCodeSearches() {
        given(marketStockQueryRepository.searchByStockNameOrStockCode(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .willReturn(new PageImpl<>(List.of(stock("005930", "삼성전자", "KOSPI"))));

        service.searchStocks(" 삼성 ", 0, 10, null);
        service.searchStocks("593", 0, 10, null);

        verify(marketStockQueryRepository).searchByStockNameOrStockCode(org.mockito.ArgumentMatchers.eq("삼성"), org.mockito.ArgumentMatchers.any());
        verify(marketStockQueryRepository).searchByStockNameOrStockCode(org.mockito.ArgumentMatchers.eq("593"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void returnsEmptyPageWhenSearchHasNoResult() {
        given(marketStockQueryRepository.searchByStockNameOrStockCode(org.mockito.ArgumentMatchers.eq("없는종목"), org.mockito.ArgumentMatchers.any()))
                .willReturn(new PageImpl<>(List.of()));

        assertThat(service.searchStocks("없는종목", 0, 10, null).content()).isEmpty();
    }

    @Test
    void getsStockDetailByNormalizedStockCode() {
        MarketStock stock = stock("005930", "삼성전자", "KOSPI");
        given(marketStockQueryRepository.findByStockCode("005930")).willReturn(Optional.of(stock));

        assertThat(service.getStock(" 005930 ").stockName()).isEqualTo("삼성전자");
    }

    @Test
    void distinguishesInvalidStockCodeFromMissingStock() {
        assertThatThrownBy(() -> service.getStock("123"))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(MarketErrorCode.INVALID_MARKET_STOCK));

        given(marketStockQueryRepository.findByStockCode("999999")).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.getStock("999999"))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(MarketErrorCode.MARKET_STOCK_NOT_FOUND));
    }

    @Test
    void rejectsInvalidPageSizeAndSort() {
        assertThatThrownBy(() -> service.getStocks(null, -1, 10, null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.getStocks(null, 0, 0, null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.getStocks(null, 0, 51, null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.getStocks(null, 0, 10, "createdAt,desc")).isInstanceOf(BusinessException.class);
    }

    private MarketStock stock(String stockCode, String stockName, String marketType) {
        return MarketStock.create(stockCode, stockName, marketType, "001", 1_000_000L, null);
    }
}
