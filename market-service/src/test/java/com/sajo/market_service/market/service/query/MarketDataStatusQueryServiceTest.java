package com.sajo.market_service.market.service.query;

import com.sajo.market_service.market.repository.query.MarketDataStatusProjection;
import com.sajo.market_service.market.repository.query.MarketDataStatusQueryRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketDataStatusQueryServiceTest {

    @Test
    void mapsAllStoredDataStatusValues() {
        MarketDataStatusQueryRepository repository = mock(MarketDataStatusQueryRepository.class);
        MarketDataStatusProjection projection = projection(2500L, 2400L,
                LocalDate.of(2026, 9, 7), 2300L, LocalDate.of(2026, 9, 7));
        when(repository.findStatus()).thenReturn(projection);

        var response = new MarketDataStatusQueryService(repository).getStatus();

        assertThat(response.totalStockCount()).isEqualTo(2500L);
        assertThat(response.dailyPriceStockCount()).isEqualTo(2400L);
        assertThat(response.latestDailyPriceDate()).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(response.indicatorStockCount()).isEqualTo(2300L);
        assertThat(response.latestIndicatorReferenceDate()).isEqualTo(LocalDate.of(2026, 9, 7));
        verify(repository).findStatus();
    }

    @Test
    void preservesZeroCountsAndNullDatesWhenNoDataExists() {
        MarketDataStatusQueryRepository repository = mock(MarketDataStatusQueryRepository.class);
        when(repository.findStatus()).thenReturn(projection(0L, 0L, null, 0L, null));

        var response = new MarketDataStatusQueryService(repository).getStatus();

        assertThat(response.totalStockCount()).isZero();
        assertThat(response.dailyPriceStockCount()).isZero();
        assertThat(response.latestDailyPriceDate()).isNull();
        assertThat(response.indicatorStockCount()).isZero();
        assertThat(response.latestIndicatorReferenceDate()).isNull();
    }

    private MarketDataStatusProjection projection(Long total, Long prices, LocalDate latestPrice,
                                                   Long indicators, LocalDate latestIndicator) {
        return new MarketDataStatusProjection() {
            public Long getTotalStockCount() { return total; }
            public Long getDailyPriceStockCount() { return prices; }
            public LocalDate getLatestDailyPriceDate() { return latestPrice; }
            public Long getIndicatorStockCount() { return indicators; }
            public LocalDate getLatestIndicatorReferenceDate() { return latestIndicator; }
        };
    }
}
