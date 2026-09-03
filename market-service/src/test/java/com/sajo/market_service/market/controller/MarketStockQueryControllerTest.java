package com.sajo.market_service.market.controller;

import com.sajo.common.exception.BusinessException;
import com.sajo.common.exception.GlobalExceptionHandler;
import com.sajo.common.response.PageResponse;
import com.sajo.market_service.market.dto.response.MarketStockResponse;
import com.sajo.market_service.market.exception.MarketErrorCode;
import com.sajo.market_service.market.service.query.MarketStockQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MarketStockQueryController.class)
@Import(GlobalExceptionHandler.class)
class MarketStockQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MarketStockQueryService marketStockQueryService;

    @Test
    void getsPagedStocksWithAllowedSort() throws Exception {
        given(marketStockQueryService.getStocks(eq("KOSPI"), eq(0), eq(10), eq("stockName,desc")))
                .willReturn(pageResponse());

        mockMvc.perform(get("/api/v1/market/stocks")
                        .param("marketType", "KOSPI")
                        .param("sort", "stockName,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].stockCode").value("005930"));
    }

    @Test
    void searchesStocks() throws Exception {
        given(marketStockQueryService.searchStocks(eq("삼성"), eq(0), eq(10), org.mockito.ArgumentMatchers.isNull()))
                .willReturn(pageResponse());

        mockMvc.perform(get("/api/v1/market/stocks/search").param("keyword", "삼성"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].stockName").value("삼성전자"));
    }

    @Test
    void returnsBadRequestForInvalidStockCode() throws Exception {
        mockMvc.perform(get("/api/v1/market/stocks/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void returnsNotFoundForMissingValidStockCode() throws Exception {
        given(marketStockQueryService.getStock("999999"))
                .willThrow(new BusinessException(MarketErrorCode.MARKET_STOCK_NOT_FOUND));

        mockMvc.perform(get("/api/v1/market/stocks/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void trimsStockCodeBeforeDetailLookup() throws Exception {
        given(marketStockQueryService.getStock("005930")).willReturn(stockResponse());

        mockMvc.perform(get("/api/v1/market/stocks/%20005930%20"))
                .andExpect(status().isOk());

        verify(marketStockQueryService).getStock("005930");
    }

    @Test
    void rejectsInvalidPageSizeAndSortAtApiBoundary() throws Exception {
        mockMvc.perform(get("/api/v1/market/stocks").param("page", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/market/stocks").param("size", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/market/stocks").param("sort", "createdAt,desc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsBlankSearchKeyword() throws Exception {
        mockMvc.perform(get("/api/v1/market/stocks/search").param("keyword", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    private PageResponse<MarketStockResponse> pageResponse() {
        return new PageResponse<>(List.of(stockResponse()), 0, 10, 1, 1);
    }

    private MarketStockResponse stockResponse() {
        return new MarketStockResponse("005930", "삼성전자", "KOSPI", "001", 1_000_000L, null);
    }
}
