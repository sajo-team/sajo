package com.sajo.market_service.market.dto.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajo.market_service.market.dto.kis.KisFinancialRatioResponse;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class FinancialRatioResponseTest {
    @Test
    void selectsLatestValidPeriodFromUnorderedSamsungFixture() throws Exception {
        try (InputStream fixture = getClass().getResourceAsStream("/fixtures/kis-financial-ratio-samsung.json")) {
            KisFinancialRatioResponse response = new ObjectMapper().readValue(fixture, KisFinancialRatioResponse.class);
            var latest = FinancialRatioResponse.latest(
                    response.output(), "005930", Instant.parse("2026-09-10T01:00:00Z"));
            assertThat(latest).hasValueSatisfying(value -> {
                assertThat(value.financialReferenceYearMonth()).isEqualTo(YearMonth.of(2026, 6));
                assertThat(value.roe()).isEqualByComparingTo(new BigDecimal("31.39"));
            });
        }
    }

    @Test
    void treatsZeroAndNegativeRoeAsValuesAndSkipsInvalidPeriods() {
        var outputs = List.of(
                new KisFinancialRatioResponse.KisFinancialRatioOutput("invalid", "99"),
                new KisFinancialRatioResponse.KisFinancialRatioOutput("202603", "0"),
                new KisFinancialRatioResponse.KisFinancialRatioOutput("202606", "-1.25"));
        assertThat(FinancialRatioResponse.latest(outputs, "005930", Instant.EPOCH))
                .hasValueSatisfying(value -> assertThat(value.roe()).isEqualByComparingTo("-1.25"));
    }

    @Test
    void skipsNewerPeriodWithoutRoeAndSelectsLatestUsablePeriod() {
        var outputs = List.of(
                new KisFinancialRatioResponse.KisFinancialRatioOutput("202603", "19.16"),
                new KisFinancialRatioResponse.KisFinancialRatioOutput("202606", ""),
                new KisFinancialRatioResponse.KisFinancialRatioOutput("202609", "not-a-number"));

        assertThat(FinancialRatioResponse.latest(outputs, "005930", Instant.EPOCH))
                .hasValueSatisfying(value -> {
                    assertThat(value.financialReferenceYearMonth()).isEqualTo(YearMonth.of(2026, 3));
                    assertThat(value.roe()).isEqualByComparingTo("19.16");
                });
    }
}
