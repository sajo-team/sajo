package com.sajo.market_service.market.controller;

import com.sajo.common.exception.GlobalExceptionHandler;
import com.sajo.market_service.market.dto.response.QuoteResponse;
import com.sajo.market_service.market.service.query.MarketQuoteQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MarketQuoteQueryController.class)
@Import(GlobalExceptionHandler.class)
class MarketQuoteQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MarketQuoteQueryService marketQuoteQueryService;

    @Test
    @DisplayName("KIS 기준 시각이 없어도 Public 현재가를 기존 필드로 반환한다")
    void returnsPublicQuoteWithoutBaseTime() throws Exception {
        UUID userId = UUID.randomUUID();
        QuoteResponse response = new QuoteResponse(
                "005930", 70_000L, 69_000L, 70_500L, 68_800L, 69_500L,
                500L, new java.math.BigDecimal("0.7194"), 123_456L, 8_610_000_000L, 4_180_000L,
                new java.math.BigDecimal("15.20"), new java.math.BigDecimal("1.35"),
                new java.math.BigDecimal("4605.00"), new java.math.BigDecimal("51850.00")
        );
        given(marketQuoteQueryService.getQuote(userId, "005930")).willReturn(response);

        mockMvc.perform(get("/quote")
                        .header("X-User-Id", userId)
                        .param("stockCode", "005930"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.stockCode").value("005930"))
                .andExpect(jsonPath("$.data.currentPrice").value(70_000))
                .andExpect(jsonPath("$.data.openPrice").value(69_000))
                .andExpect(jsonPath("$.data.highPrice").value(70_500))
                .andExpect(jsonPath("$.data.lowPrice").value(68_800))
                .andExpect(jsonPath("$.data.previousClosePrice").value(69_500))
                .andExpect(jsonPath("$.data.changePrice").value(500))
                .andExpect(jsonPath("$.data.changeRate").value(0.7194))
                .andExpect(jsonPath("$.data.accumulatedVolume").value(123_456))
                .andExpect(jsonPath("$.data.tradeAmount").value(8_610_000_000L))
                .andExpect(jsonPath("$.data.marketCapitalization").value(4_180_000))
                .andExpect(jsonPath("$.data.per").value(15.20))
                .andExpect(jsonPath("$.data.pbr").value(1.35))
                .andExpect(jsonPath("$.data.eps").value(4605.00))
                .andExpect(jsonPath("$.data.bps").value(51850.00))
                .andExpect(jsonPath("$.data.baseTime").doesNotExist());

        verify(marketQuoteQueryService).getQuote(userId, "005930");
    }

    @Test
    @DisplayName("종목 코드가 6자리 숫자가 아니면 400을 반환한다")
    void returnsBadRequestForInvalidStockCode() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(get("/quote")
                        .header("X-User-Id", userId)
                        .param("stockCode", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
