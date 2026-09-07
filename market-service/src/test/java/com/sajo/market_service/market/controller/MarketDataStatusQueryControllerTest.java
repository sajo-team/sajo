package com.sajo.market_service.market.controller;

import com.sajo.market_service.market.dto.response.MarketDataStatusResponse;
import com.sajo.market_service.market.service.query.MarketDataStatusQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MarketDataStatusQueryController.class)
class MarketDataStatusQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MarketDataStatusQueryService marketDataStatusQueryService;

    @Test
    void returnsDataStatus() throws Exception {
        given(marketDataStatusQueryService.getStatus()).willReturn(new MarketDataStatusResponse(
                2500L, 2400L, LocalDate.of(2026, 9, 7), 2300L, LocalDate.of(2026, 9, 7)));

        mockMvc.perform(get("/api/v1/market/data-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalStockCount").value(2500))
                .andExpect(jsonPath("$.data.dailyPriceStockCount").value(2400))
                .andExpect(jsonPath("$.data.latestDailyPriceDate").value("2026-09-07"))
                .andExpect(jsonPath("$.data.indicatorStockCount").value(2300))
                .andExpect(jsonPath("$.data.latestIndicatorReferenceDate").value("2026-09-07"));
    }

    @Test
    void returnsNullDatesForEmptyData() throws Exception {
        given(marketDataStatusQueryService.getStatus()).willReturn(new MarketDataStatusResponse(0, 0, null, 0, null));

        mockMvc.perform(get("/api/v1/market/data-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalStockCount").value(0))
                .andExpect(jsonPath("$.data.latestDailyPriceDate").doesNotExist())
                .andExpect(jsonPath("$.data.latestIndicatorReferenceDate").doesNotExist());
    }
}
