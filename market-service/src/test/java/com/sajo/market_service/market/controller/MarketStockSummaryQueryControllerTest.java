package com.sajo.market_service.market.controller;

import com.sajo.common.exception.BusinessException;
import com.sajo.common.exception.GlobalExceptionHandler;
import com.sajo.market_service.market.dto.response.MarketStockIndicatorResponse;
import com.sajo.market_service.market.dto.response.MarketStockResponse;
import com.sajo.market_service.market.dto.response.MarketStockSummaryResponse;
import com.sajo.market_service.market.dto.response.PublicQuoteResponse;
import com.sajo.market_service.market.exception.MarketErrorCode;
import com.sajo.market_service.market.domain.FinancialPeriodType;
import com.sajo.market_service.market.service.query.MarketStockSummaryQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MarketStockSummaryQueryController.class)
@Import(GlobalExceptionHandler.class)
class MarketStockSummaryQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MarketStockSummaryQueryService marketStockSummaryQueryService;

    @Test
    void rejectsMissingUserId() throws Exception {
        mockMvc.perform(get("/api/v1/market/stocks/005930/summary"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0001"));
    }

    @Test
    void rejectsMalformedUserId() throws Exception {
        mockMvc.perform(get("/api/v1/market/stocks/005930/summary")
                        .header("X-User-Id", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0001"));
    }

    @Test
    void rejectsInvalidStockCode() throws Exception {
        mockMvc.perform(get("/api/v1/market/stocks/abc/summary")
                        .header("X-User-Id", UUID.randomUUID()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsComposedSummaryWithoutManagementFields() throws Exception {
        UUID userId = UUID.randomUUID();
        given(marketStockSummaryQueryService.getSummary(userId, "005930"))
                .willReturn(new MarketStockSummaryResponse(
                        new MarketStockResponse("005930", "삼성전자", "KOSPI", "001", 1000L, new BigDecimal("1000000")),
                        new PublicQuoteResponse("005930", 71800L, 70000L, 72000L, 69000L, 71000L,
                                800L, new BigDecimal("1.12"), 123456L, 987654L, 1000000L,
                                new BigDecimal("15.2"), new BigDecimal("1.3"), new BigDecimal("4605"), new BigDecimal("51850")),
                        new MarketStockIndicatorResponse(null, new BigDecimal("15.2"),
                                new BigDecimal("1.3"), new BigDecimal("31.39"),
                                Instant.parse("2026-09-10T01:00:00Z"), FinancialPeriodType.QUARTER, "2026-06",
                                Instant.parse("2026-09-10T01:00:01Z"))));

        mockMvc.perform(get("/api/v1/market/stocks/005930/summary").header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stock.stockCode").value("005930"))
                .andExpect(jsonPath("$.data.stock.stockName").value("삼성전자"))
                .andExpect(jsonPath("$.data.stock.marketType").value("KOSPI"))
                .andExpect(jsonPath("$.data.quote.currentPrice").value(71800))
                .andExpect(jsonPath("$.data.quote.changeRate").value(1.12))
                .andExpect(jsonPath("$.data.indicator.financialReferenceYearMonth").value("2026-06"))
                .andExpect(jsonPath("$.data.indicator.per").value(15.2))
                .andExpect(jsonPath("$.data.indicator.pbr").value(1.3))
                .andExpect(jsonPath("$.data.stock.id").doesNotExist())
                .andExpect(jsonPath("$.data.stock.stockId").doesNotExist())
                .andExpect(jsonPath("$.data.stock.createdAt").doesNotExist())
                .andExpect(jsonPath("$.data.stock.updatedAt").doesNotExist())
                .andExpect(jsonPath("$.data.stock.accessToken").doesNotExist())
                .andExpect(jsonPath("$.data.stock.secretKey").doesNotExist())
                .andExpect(jsonPath("$.data.quote.accessToken").doesNotExist())
                .andExpect(jsonPath("$.data.quote.secretKey").doesNotExist())
                .andExpect(jsonPath("$.data.indicator.id").doesNotExist())
                .andExpect(jsonPath("$.data.indicator.createdAt").doesNotExist())
                .andExpect(jsonPath("$.data.indicator.updatedAt").doesNotExist())
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andExpect(jsonPath("$.data.secretKey").doesNotExist());
    }

    @Test
    void returnsNotFoundWhenStockDoesNotExist() throws Exception {
        UUID userId = UUID.randomUUID();
        given(marketStockSummaryQueryService.getSummary(userId, "999999"))
                .willThrow(new BusinessException(MarketErrorCode.MARKET_STOCK_NOT_FOUND));

        mockMvc.perform(get("/api/v1/market/stocks/999999/summary").header("X-User-Id", userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MARKET_0006"));
    }

    @Test
    void propagatesQuoteFailureResponse() throws Exception {
        UUID userId = UUID.randomUUID();
        given(marketStockSummaryQueryService.getSummary(userId, "005930"))
                .willThrow(new BusinessException(MarketErrorCode.KIS_QUOTE_RESPONSE_INVALID));

        mockMvc.perform(get("/api/v1/market/stocks/005930/summary").header("X-User-Id", userId))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("MARKET_0004"));
    }

    @Test
    void returnsOkWithNullIndicatorWhenIndicatorIsMissing() throws Exception {
        UUID userId = UUID.randomUUID();
        given(marketStockSummaryQueryService.getSummary(userId, "005930"))
                .willReturn(new MarketStockSummaryResponse(
                        new MarketStockResponse("005930", "삼성전자", "KOSPI", null, null, null),
                        new PublicQuoteResponse("005930", 71800L, null, null, null, null, null, null,
                                null, null, null, null, null, null, null), null));

        mockMvc.perform(get("/api/v1/market/stocks/005930/summary").header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quote.currentPrice").value(71800))
                .andExpect(jsonPath("$.data.indicator").doesNotExist());
    }
}
