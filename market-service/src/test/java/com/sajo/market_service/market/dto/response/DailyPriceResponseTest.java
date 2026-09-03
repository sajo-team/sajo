package com.sajo.market_service.market.dto.response;

import com.sajo.market_service.market.dto.kis.KisDailyPriceResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DailyPriceResponseTest {

    @Test
    void mapsKisDailyPriceFieldsToInternalResponse() {
        DailyPriceResponse response = DailyPriceResponse.from(
                output("20260901", "69000", "70500", "68800", "70000", "123456", "8610000000"), "005930"
        ).orElseThrow();

        assertThat(response.tradeDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(response.openPrice()).isEqualTo(69000L);
        assertThat(response.highPrice()).isEqualTo(70500L);
        assertThat(response.lowPrice()).isEqualTo(68800L);
        assertThat(response.closePrice()).isEqualTo(70000L);
        assertThat(response.volume()).isEqualTo(123456L);
        assertThat(response.tradeAmount()).isEqualTo(8610000000L);
    }

    @Test
    void treatsBlankOrMalformedOptionalNumericFieldsAsNull() {
        DailyPriceResponse response = DailyPriceResponse.from(
                output("20260901", "", "invalid", "68800", "70000", "", "invalid"), "005930"
        ).orElseThrow();

        assertThat(response.openPrice()).isNull();
        assertThat(response.highPrice()).isNull();
        assertThat(response.lowPrice()).isEqualTo(68800L);
        assertThat(response.volume()).isNull();
        assertThat(response.tradeAmount()).isNull();
    }

    @Test
    void skipsRowWhenRequiredTradeDateOrClosePriceIsInvalid() {
        assertThat(DailyPriceResponse.from(
                output("invalid-date", "69000", "70500", "68800", "70000", "123456", "8610000000"), "005930"
        )).isEmpty();
        assertThat(DailyPriceResponse.from(
                output("20260901", "69000", "70500", "68800", "invalid", "123456", "8610000000"), "005930"
        )).isEmpty();
    }

    @Test
    void skipsNullOutputOrNegativeClosePrice() {
        assertThat(DailyPriceResponse.from(null, "005930")).isEmpty();
        assertThat(DailyPriceResponse.from(
                output("20260901", "69000", "70500", "68800", "-1", "123456", "8610000000"), "005930"
        )).isEmpty();
    }

    @Test
    void treatsNegativeOptionalNumericFieldsAsNull() {
        DailyPriceResponse response = DailyPriceResponse.from(
                output("20260901", "-1", "-1", "-1", "70000", "-1", "-1"), "005930"
        ).orElseThrow();

        assertThat(response.openPrice()).isNull();
        assertThat(response.highPrice()).isNull();
        assertThat(response.lowPrice()).isNull();
        assertThat(response.volume()).isNull();
        assertThat(response.tradeAmount()).isNull();
    }

    private KisDailyPriceResponse.KisDailyPriceOutput output(
            String tradeDate, String openPrice, String highPrice, String lowPrice, String closePrice,
            String volume, String tradeAmount
    ) {
        return new KisDailyPriceResponse.KisDailyPriceOutput(
                tradeDate, openPrice, highPrice, lowPrice, closePrice, volume, tradeAmount);
    }
}
