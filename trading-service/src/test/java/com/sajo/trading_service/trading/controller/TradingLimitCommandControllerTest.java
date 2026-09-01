package com.sajo.trading_service.trading.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajo.common.exception.GlobalExceptionHandler;
import com.sajo.trading_service.trading.controller.dto.request.TradingLimitCreateRequest;
import com.sajo.trading_service.trading.controller.dto.response.TradingLimitCreateResponse;
import com.sajo.trading_service.trading.service.command.TradingLimitCommandService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TradingLimitCommandController.class)
@Import(GlobalExceptionHandler.class)
class TradingLimitCommandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TradingLimitCommandService tradingLimitCommandService;

    @Test
    @DisplayName("자동매매 공통 한도를 생성하면 201을 반환한다")
    void createTradingLimit() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID tradingLimitId = UUID.randomUUID();

        TradingLimitCreateRequest request = new TradingLimitCreateRequest(
                3_000_000L,
                10,
                new BigDecimal("5.00")
        );

        TradingLimitCreateResponse response =
                new TradingLimitCreateResponse(
                        tradingLimitId,
                        3_000_000L,
                        10,
                        new BigDecimal("5.00"),
                        Instant.now()
                );

        given(tradingLimitCommandService.createTradingLimit(
                eq(userId),
                any(TradingLimitCreateRequest.class)
        )).willReturn(response);

        // when & then
        mockMvc.perform(
                        post("/api/v1/trading-limits")
                                .param("userId", userId.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tradingLimitId")
                        .value(tradingLimitId.toString()))
                .andExpect(jsonPath("$.data.dailyMaxOrderAmount")
                        .value(3_000_000))
                .andExpect(jsonPath("$.data.dailyMaxOrderCount")
                        .value(10))
                .andExpect(jsonPath("$.data.dailyLossLimitRate")
                        .value(5.00));
    }

    @Test
    @DisplayName("일일 최대 주문 금액이 0 이하이면 400을 반환한다")
    void createTradingLimitInvalidOrderAmount() throws Exception {
        // given
        UUID userId = UUID.randomUUID();

        TradingLimitCreateRequest request = new TradingLimitCreateRequest(
                0L,
                10,
                new BigDecimal("5.00")
        );

        // when & then
        mockMvc.perform(
                        post("/api/v1/trading-limits")
                                .param("userId", userId.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}