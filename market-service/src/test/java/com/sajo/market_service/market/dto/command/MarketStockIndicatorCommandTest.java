package com.sajo.market_service.market.dto.command;

import com.sajo.market_service.market.dto.response.QuoteResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class MarketStockIndicatorCommandTest {

    @Test
    void createsSnapshotOnlyWhenKisBusinessDateAndOneValuationMetricExist() {
        QuoteResponse quote = quote(LocalDate.of(2026, 9, 4), new BigDecimal("15.2"), null);

        var indicator = MarketStockIndicatorCommand.from(quote);

        assertThat(indicator).hasValueSatisfying(value -> {
            assertThat(value.referenceDate()).isEqualTo(LocalDate.of(2026, 9, 4));
            assertThat(value.per()).isEqualByComparingTo("15.2");
            assertThat(value.bps()).isEqualByComparingTo("51850");
        });
    }

    @Test
    void skipsSnapshotWithoutKisBusinessDateOrPerAndPbr() {
        assertThat(MarketStockIndicatorCommand.from(quote(null, new BigDecimal("15.2"), null))).isEmpty();
        assertThat(MarketStockIndicatorCommand.from(quote(LocalDate.of(2026, 9, 4), null, null))).isEmpty();
    }

    @Test
    void permitsZeroAndNegativeKisIndicatorsWithoutInventingAValidationPolicy() {
        QuoteResponse quote = quote(LocalDate.of(2026, 9, 4), BigDecimal.ZERO, new BigDecimal("-1.3"));

        assertThat(MarketStockIndicatorCommand.from(quote)).hasValueSatisfying(value -> {
            assertThat(value.per()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(value.pbr()).isEqualByComparingTo("-1.3");
        });
    }

    private QuoteResponse quote(LocalDate businessDate, BigDecimal per, BigDecimal pbr) {
        return new QuoteResponse("005930", 70_000L, null, null, null, null, null, null, null, null, null,
                per, pbr, new BigDecimal("4605"), new BigDecimal("51850"), "2026-09-04T14:30:00+09:00", businessDate);
    }
}
