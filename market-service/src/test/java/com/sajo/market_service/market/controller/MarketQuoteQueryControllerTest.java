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
    @DisplayName("현재가 조회 요청을 Query Service에 전달하고 200을 반환한다")
    void getQuote() throws Exception {
        UUID userId = UUID.randomUUID();
        QuoteResponse response = new QuoteResponse(
                "005930", 70_000L, 69_000L, 70_500L, 68_800L, 69_500L,
                500L, null, 123_456L, 8_610_000_000L, 4_180_000L,
                null, null, null, null
        );
        given(marketQuoteQueryService.getQuote(userId, "005930")).willReturn(response);

        mockMvc.perform(get("/quote")
                        .header("X-User-Id", userId)
                        .param("stockCode", "005930"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.stockCode").value("005930"))
                .andExpect(jsonPath("$.data.currentPrice").value(70_000));

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
