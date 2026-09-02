package com.sajo.market_service.market.dto.response;

import com.sajo.market_service.market.dto.kis.KisDailyPriceResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DailyPriceResponseTest {

    @Test
    void mapsKisDailyPriceFieldsToInternalResponse() {
        KisDailyPriceResponse.KisDailyPriceOutput output = new KisDailyPriceResponse.KisDailyPriceOutput(
                "20260901", "69000", "70500", "68800", "70000", "123456", "8610000000");

        DailyPriceResponse response = DailyPriceResponse.from(output);

        assertEquals(LocalDate.of(2026, 9, 1), response.tradeDate());
        assertEquals(69000L, response.openPrice());
        assertEquals(70500L, response.highPrice());
        assertEquals(68800L, response.lowPrice());
        assertEquals(70000L, response.closePrice());
        assertEquals(123456L, response.volume());
        assertEquals(8610000000L, response.tradeAmount());
    }
}
