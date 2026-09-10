package com.sajo.market_service.market.controller;

import com.sajo.common.exception.BusinessException;
import com.sajo.common.exception.GlobalExceptionHandler;
import com.sajo.market_service.market.dto.response.MarketStockIndicatorResponse;
import com.sajo.market_service.market.exception.MarketErrorCode;
import com.sajo.market_service.market.domain.FinancialPeriodType;
import com.sajo.market_service.market.service.query.MarketStockIndicatorQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MarketStockIndicatorQueryController.class)
@Import(GlobalExceptionHandler.class)
class MarketStockIndicatorQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MarketStockIndicatorQueryService marketStockIndicatorQueryService;

    @Test
    void returnsLatestStoredIndicator() throws Exception {
        given(marketStockIndicatorQueryService.getLatestIndicator("005930")).willReturn(response());

        mockMvc.perform(get("/api/v1/market/stocks/005930/indicators"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.financialReferenceYearMonth").value("2026-06"))
                .andExpect(jsonPath("$.data.valuationFetchedAt").value("2026-09-10T01:00:00Z"))
                .andExpect(jsonPath("$.data.per").value(12.34));
    }

    @Test
    void rejectsInvalidCodeAndDistinguishesMissingIndicator() throws Exception {
        mockMvc.perform(get("/api/v1/market/stocks/abc/indicators"))
                .andExpect(status().isBadRequest());

        given(marketStockIndicatorQueryService.getLatestIndicator("005930"))
                .willThrow(new BusinessException(MarketErrorCode.MARKET_STOCK_INDICATOR_NOT_FOUND));
        mockMvc.perform(get("/api/v1/market/stocks/005930/indicators"))
                .andExpect(status().isNotFound());
    }

    @Test
    void usesDefaultLimitAndReturnsIndicatorHistory() throws Exception {
        given(marketStockIndicatorQueryService.getIndicatorHistory(eq("005930"), eq(8)))
                .willReturn(List.of(response()));

        mockMvc.perform(get("/api/v1/market/stocks/005930/indicators/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].financialReferenceYearMonth").value("2026-06"));
    }

    @Test
    void returnsEmptyArrayWhenNoIndicatorHistoryExists() throws Exception {
        given(marketStockIndicatorQueryService.getIndicatorHistory(eq("005930"), eq(8)))
                .willReturn(List.of());

        mockMvc.perform(get("/api/v1/market/stocks/005930/indicators/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void rejectsInvalidStockCodeAndLimitRangeForHistory() throws Exception {
        mockMvc.perform(get("/api/v1/market/stocks/abc/indicators/history"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/market/stocks/005930/indicators/history").param("limit", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/market/stocks/005930/indicators/history").param("limit", "41"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsNotFoundForMissingStockInHistory() throws Exception {
        given(marketStockIndicatorQueryService.getIndicatorHistory("999999", 8))
                .willThrow(new BusinessException(MarketErrorCode.MARKET_STOCK_NOT_FOUND));

        mockMvc.perform(get("/api/v1/market/stocks/999999/indicators/history"))
                .andExpect(status().isNotFound());
    }

    private MarketStockIndicatorResponse response() {
        return new MarketStockIndicatorResponse(null, new BigDecimal("12.34"),
                new BigDecimal("1.23"), null, null, new BigDecimal("8.76"),
                Instant.parse("2026-09-10T01:00:00Z"), FinancialPeriodType.QUARTER, "2026-06",
                Instant.parse("2026-09-10T01:00:01Z"));
    }
}
