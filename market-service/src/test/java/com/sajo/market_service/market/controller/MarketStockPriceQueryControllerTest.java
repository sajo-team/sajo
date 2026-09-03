package com.sajo.market_service.market.controller;

import com.sajo.common.exception.BusinessException;
import com.sajo.common.exception.GlobalExceptionHandler;
import com.sajo.market_service.market.dto.response.MarketStockPriceResponse;
import com.sajo.market_service.market.exception.MarketErrorCode;
import com.sajo.market_service.market.service.query.MarketStockPriceQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MarketStockPriceQueryController.class)
@Import(GlobalExceptionHandler.class)
class MarketStockPriceQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MarketStockPriceQueryService marketStockPriceQueryService;

    @Test
    void usesDefaultDaysAndReturnsSavedDailyPrices() throws Exception {
        given(marketStockPriceQueryService.getRecentDailyPrices(eq("005930"), eq(30)))
                .willReturn(List.of(priceResponse()));

        mockMvc.perform(get("/api/v1/market/stocks/005930/prices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].tradeDate").value("2026-09-01"))
                .andExpect(jsonPath("$.data[0].closePrice").value(70000))
                .andExpect(jsonPath("$.data[0].currentPrice").doesNotExist());
    }

    @Test
    void rejectsInvalidStockCodeAndDaysRange() throws Exception {
        mockMvc.perform(get("/api/v1/market/stocks/abc/prices"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/market/stocks/005930/prices").param("days", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/market/stocks/005930/prices").param("days", "366"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsNotFoundForMissingStock() throws Exception {
        given(marketStockPriceQueryService.getRecentDailyPrices("999999", 30))
                .willThrow(new BusinessException(MarketErrorCode.MARKET_STOCK_NOT_FOUND));

        mockMvc.perform(get("/api/v1/market/stocks/999999/prices"))
                .andExpect(status().isNotFound());
    }

    private MarketStockPriceResponse priceResponse() {
        return new MarketStockPriceResponse(LocalDate.of(2026, 9, 1), 69_000L, 71_000L, 68_000L,
                70_000L, 123_456L, 8_610_000_000L);
    }
}
