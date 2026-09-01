package com.sajo.market_service.strategy.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajo.common.exception.GlobalExceptionHandler;
import com.sajo.market_service.strategy.controller.dto.request.StrategyCreateRequest;
import com.sajo.market_service.strategy.controller.dto.response.StrategyCreateResponse;
import com.sajo.market_service.strategy.domain.StrategyStatus;
import com.sajo.market_service.strategy.service.command.StrategyCommandService;
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

@WebMvcTest(StrategyCommandController.class)
@Import(GlobalExceptionHandler.class)
class StrategyCommandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private StrategyCommandService strategyCommandService;

    @Test
    @DisplayName("전략을 생성하면 201과 INACTIVE 상태를 반환한다")
    void createStrategy() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID stockId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        StrategyCreateRequest request = new StrategyCreateRequest(
                stockId, "005930", "삼성전자 눌림목 전략",
                70_000L, 80_000L, new BigDecimal("5.0000"), null,
                3_000_000L, null, null, null
        );

        StrategyCreateResponse response = new StrategyCreateResponse(
                strategyId, stockId, "005930", "삼성전자 눌림목 전략",
                70_000L, 80_000L, new BigDecimal("5.0000"), null,
                3_000_000L, null, null, null,
                StrategyStatus.INACTIVE, Instant.now()
        );

        given(strategyCommandService.createStrategy(eq(userId), any(StrategyCreateRequest.class)))
                .willReturn(response);

        // when & then
        mockMvc.perform(
                        post("/api/v1/strategies")
                                .param("userId", userId.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.strategyId").value(strategyId.toString()))
                .andExpect(jsonPath("$.data.stockCode").value("005930"))
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));
    }

    @Test
    @DisplayName("전략명이 비어있으면 400을 반환한다")
    void createStrategyBlankName() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        StrategyCreateRequest request = new StrategyCreateRequest(
                UUID.randomUUID(), "005930", "",
                70_000L, 80_000L, new BigDecimal("5.0000"), null,
                3_000_000L, null, null, null
        );

        // when & then
        mockMvc.perform(
                        post("/api/v1/strategies")
                                .param("userId", userId.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("매수 조건 가격이 0 이하이면 400을 반환한다")
    void createStrategyInvalidBuyConditionPrice() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        StrategyCreateRequest request = new StrategyCreateRequest(
                UUID.randomUUID(), "005930", "삼성전자 눌림목 전략",
                0L, 80_000L, new BigDecimal("5.0000"), null,
                3_000_000L, null, null, null
        );

        // when & then
        mockMvc.perform(
                        post("/api/v1/strategies")
                                .param("userId", userId.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
