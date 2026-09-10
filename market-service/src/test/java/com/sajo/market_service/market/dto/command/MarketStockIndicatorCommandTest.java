package com.sajo.market_service.market.dto.command;

import com.sajo.market_service.market.domain.FinancialPeriodType;
import com.sajo.market_service.market.dto.response.FinancialRatioResponse;
import com.sajo.market_service.market.dto.response.QuoteResponse;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import static org.assertj.core.api.Assertions.assertThat;

class MarketStockIndicatorCommandTest {
    private static final Instant VALUATION_TIME = Instant.parse("2026-09-10T01:00:00Z");
    private static final Instant FINANCIAL_TIME = Instant.parse("2026-09-10T01:00:01Z");

    @Test
    void combinesValuationAndQuarterlyFinancialMetadata() {
        var command = MarketStockIndicatorCommand.from(quote("15.2", "1.3"), financial("31.39"));
        assertThat(command).hasValueSatisfying(value -> {
            assertThat(value.valuationFetchedAt()).isEqualTo(VALUATION_TIME);
            assertThat(value.roe()).isEqualByComparingTo("31.39");
            assertThat(value.financialPeriodType()).isEqualTo(FinancialPeriodType.QUARTER);
            assertThat(value.financialReferenceYearMonth()).isEqualTo(YearMonth.of(2026, 6));
            assertThat(value.financialFetchedAt()).isEqualTo(FINANCIAL_TIME);
        });
    }

    @Test
    void requiresBothSourcesButAcceptsZeroAndNegativeRoe() {
        assertThat(MarketStockIndicatorCommand.from(quote(null, null), financial("1"))).isEmpty();
        assertThat(MarketStockIndicatorCommand.from(quote("15", null), null)).isEmpty();
        assertThat(MarketStockIndicatorCommand.from(quote("15", null), financial(null))).isEmpty();
        assertThat(MarketStockIndicatorCommand.from(quote("15", null), financial("0"))).isPresent();
        assertThat(MarketStockIndicatorCommand.from(quote("15", null), financial("-1.25"))).isPresent();
    }

    private QuoteResponse quote(String per, String pbr) {
        return new QuoteResponse("005930", 70000L, null, null, null, null, null, null, null, null,
                null, decimal(per), decimal(pbr), null, null, null, VALUATION_TIME);
    }
    private FinancialRatioResponse financial(String roe) {
        return new FinancialRatioResponse(YearMonth.of(2026, 6), decimal(roe), FINANCIAL_TIME);
    }
    private BigDecimal decimal(String value) { return value == null ? null : new BigDecimal(value); }
}
