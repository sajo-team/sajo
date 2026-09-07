package com.sajo.market_service.market.controller;

import com.sajo.common.exception.BusinessException;
import com.sajo.common.exception.GlobalExceptionHandler;
import com.sajo.market_service.market.dto.response.MarketStockIndicatorResponse;
import com.sajo.market_service.market.exception.MarketErrorCode;
import com.sajo.market_service.market.service.query.MarketStockIndicatorQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

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
                .andExpect(jsonPath("$.data.referenceDate").value("2026-09-01"))
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

    private MarketStockIndicatorResponse response() {
        return new MarketStockIndicatorResponse(LocalDate.of(2026, 9, 1), new BigDecimal("12.34"),
                new BigDecimal("1.23"), new BigDecimal("1000"), new BigDecimal("20000"), new BigDecimal("8.76"));
    }
}
