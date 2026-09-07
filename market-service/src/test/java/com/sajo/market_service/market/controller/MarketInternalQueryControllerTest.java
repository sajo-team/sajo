package com.sajo.market_service.market.controller;

import com.sajo.common.exception.GlobalExceptionHandler;
import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.market.exception.MarketErrorCode;
import com.sajo.market_service.market.controller.dto.response.InternalStockQuoteResponse;
import com.sajo.market_service.market.controller.dto.response.InternalStockIndicatorResponse;
import com.sajo.market_service.market.service.query.MarketInternalQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MarketInternalQueryController.class)
@Import(GlobalExceptionHandler.class)
class MarketInternalQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MarketInternalQueryService marketInternalQueryService;

    @Test
    void returnsKisBaseTimeWithSeoulOffset() throws Exception {
        UUID userId = UUID.randomUUID();
        given(marketInternalQueryService.getQuote(userId, "005930"))
                .willReturn(new InternalStockQuoteResponse("005930", 71_800L, OffsetDateTime.parse("2026-09-04T14:30:00+09:00")));

        mockMvc.perform(get("/internal/v1/stocks/005930/quote").header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.baseTime").value("2026-09-04T14:30:00+09:00"));
    }

    @Test
    void returnsOnlyStrategyRequiredIndicatorFields() throws Exception {
        given(marketInternalQueryService.getIndicator("005930"))
                .willReturn(new InternalStockIndicatorResponse("005930", new BigDecimal("15.2"),
                        new BigDecimal("1.3"), new BigDecimal("8.7"), LocalDate.of(2026, 9, 3)));

        mockMvc.perform(get("/internal/v1/stocks/005930/indicator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockCode").value("005930"))
                .andExpect(jsonPath("$.data.eps").doesNotExist())
                .andExpect(jsonPath("$.data.bps").doesNotExist());
    }

    @Test
    void returnsBadRequestWhenUserIdHeaderIsMissing() throws Exception {
        mockMvc.perform(get("/internal/v1/stocks/005930/quote"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0001"));
    }

    @Test
    void returnsBadRequestWhenUserIdHeaderIsNotUuid() throws Exception {
        mockMvc.perform(get("/internal/v1/stocks/005930/quote").header("X-User-Id", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0001"));
    }

    @Test
    void returnsBadRequestWhenIndicatorStockCodeIsInvalid() throws Exception {
        mockMvc.perform(get("/internal/v1/stocks/abc/indicator"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0001"));
    }

    @Test
    void returnsBadRequestWhenQuoteStockCodeIsInvalid() throws Exception {
        mockMvc.perform(get("/internal/v1/stocks/0059300/quote").header("X-User-Id", UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0001"));
    }

    @Test
    void returnsKisResponseErrorWhenInternalQuoteBaseTimeIsInvalid() throws Exception {
        UUID userId = UUID.randomUUID();
        given(marketInternalQueryService.getQuote(userId, "005930"))
                .willThrow(new BusinessException(MarketErrorCode.KIS_QUOTE_RESPONSE_INVALID));

        mockMvc.perform(get("/internal/v1/stocks/005930/quote").header("X-User-Id", userId))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("MARKET_0004"));
    }

    @Test
    void propagatesMissingStockAndIndicatorErrors() throws Exception {
        given(marketInternalQueryService.getIndicator("999999"))
                .willThrow(new BusinessException(MarketErrorCode.MARKET_STOCK_NOT_FOUND));
        given(marketInternalQueryService.getIndicator("005930"))
                .willThrow(new BusinessException(MarketErrorCode.MARKET_STOCK_INDICATOR_NOT_FOUND));

        mockMvc.perform(get("/internal/v1/stocks/999999/indicator"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MARKET_0006"));
        mockMvc.perform(get("/internal/v1/stocks/005930/indicator"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MARKET_0007"));
    }
}
