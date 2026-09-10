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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    @Test
    void returnsDailyPricesForDateRangeAndIgnoresDays() throws Exception {
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 9, 9);

        given(marketStockPriceQueryService.getDailyPrices(eq("005930"), eq(startDate), eq(endDate)))
                .willReturn(List.of(priceResponse()));

        mockMvc.perform(get("/api/v1/market/stocks/005930/prices")
                        .param("startDate", "2026-08-01")
                        .param("endDate", "2026-09-09")
                        // days가 함께 와도 무시되어야 한다.
                        .param("days", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].tradeDate").value("2026-09-01"));

        verify(marketStockPriceQueryService, never()).getRecentDailyPrices(any(), anyInt());
    }

    @Test
    void rejectsWhenOnlyStartDateProvided() throws Exception {
        mockMvc.perform(get("/api/v1/market/stocks/005930/prices").param("startDate", "2026-08-01"))
                .andExpect(status().isBadRequest());

        verify(marketStockPriceQueryService, never()).getDailyPrices(any(), any(), any());
        verify(marketStockPriceQueryService, never()).getRecentDailyPrices(any(), anyInt());
    }

    @Test
    void rejectsWhenOnlyEndDateProvided() throws Exception {
        mockMvc.perform(get("/api/v1/market/stocks/005930/prices").param("endDate", "2026-09-09"))
                .andExpect(status().isBadRequest());

        verify(marketStockPriceQueryService, never()).getDailyPrices(any(), any(), any());
        verify(marketStockPriceQueryService, never()).getRecentDailyPrices(any(), anyInt());
    }

    @Test
    void returnsNotFoundForMissingStockInDateRange() throws Exception {
        given(marketStockPriceQueryService.getDailyPrices(
                eq("999999"), any(LocalDate.class), any(LocalDate.class)))
                .willThrow(new BusinessException(MarketErrorCode.MARKET_STOCK_NOT_FOUND));

        mockMvc.perform(get("/api/v1/market/stocks/999999/prices")
                        .param("startDate", "2026-08-01")
                        .param("endDate", "2026-09-09"))
                .andExpect(status().isNotFound());
    }

    @Test
    void chartUsesDefaultDaysAndReturnsSameFieldsAsPrices() throws Exception {
        given(marketStockPriceQueryService.getRecentDailyPrices(eq("005930"), eq(30)))
                .willReturn(List.of(priceResponse()));

        mockMvc.perform(get("/api/v1/market/stocks/005930/chart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].tradeDate").value("2026-09-01"))
                .andExpect(jsonPath("$.data[0].openPrice").value(69000))
                .andExpect(jsonPath("$.data[0].highPrice").value(71000))
                .andExpect(jsonPath("$.data[0].lowPrice").value(68000))
                .andExpect(jsonPath("$.data[0].closePrice").value(70000))
                .andExpect(jsonPath("$.data[0].accumulatedVolume").value(123456))
                .andExpect(jsonPath("$.data[0].accumulatedTradeAmount").value(8_610_000_000L));
    }

    @Test
    void chartReturnsDataForDateRangeAndIgnoresDays() throws Exception {
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 9, 9);

        given(marketStockPriceQueryService.getDailyPrices(eq("005930"), eq(startDate), eq(endDate)))
                .willReturn(List.of(priceResponse()));

        mockMvc.perform(get("/api/v1/market/stocks/005930/chart")
                        .param("startDate", "2026-08-01")
                        .param("endDate", "2026-09-09")
                        .param("days", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].tradeDate").value("2026-09-01"));

        verify(marketStockPriceQueryService, never()).getRecentDailyPrices(any(), anyInt());
    }

    @Test
    void rejectsInvalidStockCodeAndDaysRangeForChart() throws Exception {
        mockMvc.perform(get("/api/v1/market/stocks/abc/chart"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/market/stocks/005930/chart").param("days", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/market/stocks/005930/chart").param("days", "366"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsChartWhenOnlyStartDateProvided() throws Exception {
        mockMvc.perform(get("/api/v1/market/stocks/005930/chart").param("startDate", "2026-08-01"))
                .andExpect(status().isBadRequest());

        verify(marketStockPriceQueryService, never()).getDailyPrices(any(), any(), any());
        verify(marketStockPriceQueryService, never()).getRecentDailyPrices(any(), anyInt());
    }

    @Test
    void returnsNotFoundForMissingStockOnChart() throws Exception {
        given(marketStockPriceQueryService.getRecentDailyPrices("999999", 30))
                .willThrow(new BusinessException(MarketErrorCode.MARKET_STOCK_NOT_FOUND));

        mockMvc.perform(get("/api/v1/market/stocks/999999/chart"))
                .andExpect(status().isNotFound());
    }

    @Test
    void volumeUsesDefaultDaysAndReturnsOnlyVolumeFields() throws Exception {
        given(marketStockPriceQueryService.getRecentDailyPrices(eq("005930"), eq(30)))
                .willReturn(List.of(priceResponse()));

        mockMvc.perform(get("/api/v1/market/stocks/005930/volume"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].tradeDate").value("2026-09-01"))
                .andExpect(jsonPath("$.data[0].accumulatedVolume").value(123456))
                .andExpect(jsonPath("$.data[0].accumulatedTradeAmount").value(8_610_000_000L))
                .andExpect(jsonPath("$.data[0].openPrice").doesNotExist())
                .andExpect(jsonPath("$.data[0].closePrice").doesNotExist());
    }

    @Test
    void volumeReturnsDataForDateRangeAndIgnoresDays() throws Exception {
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 9, 9);

        given(marketStockPriceQueryService.getDailyPrices(eq("005930"), eq(startDate), eq(endDate)))
                .willReturn(List.of(priceResponse()));

        mockMvc.perform(get("/api/v1/market/stocks/005930/volume")
                        .param("startDate", "2026-08-01")
                        .param("endDate", "2026-09-09")
                        .param("days", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].tradeDate").value("2026-09-01"));

        verify(marketStockPriceQueryService, never()).getRecentDailyPrices(any(), anyInt());
    }

    @Test
    void rejectsInvalidStockCodeAndDaysRangeForVolume() throws Exception {
        mockMvc.perform(get("/api/v1/market/stocks/abc/volume"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/market/stocks/005930/volume").param("days", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/market/stocks/005930/volume").param("days", "366"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsVolumeWhenOnlyEndDateProvided() throws Exception {
        mockMvc.perform(get("/api/v1/market/stocks/005930/volume").param("endDate", "2026-09-09"))
                .andExpect(status().isBadRequest());

        verify(marketStockPriceQueryService, never()).getDailyPrices(any(), any(), any());
        verify(marketStockPriceQueryService, never()).getRecentDailyPrices(any(), anyInt());
    }

    @Test
    void returnsNotFoundForMissingStockOnVolume() throws Exception {
        given(marketStockPriceQueryService.getDailyPrices(
                eq("999999"), any(LocalDate.class), any(LocalDate.class)))
                .willThrow(new BusinessException(MarketErrorCode.MARKET_STOCK_NOT_FOUND));

        mockMvc.perform(get("/api/v1/market/stocks/999999/volume")
                        .param("startDate", "2026-08-01")
                        .param("endDate", "2026-09-09"))
                .andExpect(status().isNotFound());
    }

    private MarketStockPriceResponse priceResponse() {
        return new MarketStockPriceResponse(LocalDate.of(2026, 9, 1), 69_000L, 71_000L, 68_000L,
                70_000L, 123_456L, 8_610_000_000L);
    }
}
