package com.sajo.market_service.strategy.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajo.common.exception.BusinessException;
import com.sajo.common.exception.GlobalExceptionHandler;
import com.sajo.market_service.strategy.controller.dto.request.BacktestCreateRequest;
import com.sajo.market_service.strategy.controller.dto.response.BacktestCreateResponse;
import com.sajo.market_service.strategy.domain.BacktestStatus;
import com.sajo.market_service.strategy.exception.StrategyErrorCode;
import com.sajo.market_service.strategy.service.command.BacktestCommandService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BacktestCommandController.class)
@Import(GlobalExceptionHandler.class)
class BacktestCommandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private BacktestCommandService backtestCommandService;

    @Test
    @DisplayName("백테스트 실행을 요청하면 201과 REQUESTED 상태를 반환한다.")
    void createBacktest() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();
        UUID backtestId = UUID.randomUUID();

        BacktestCreateRequest request = new BacktestCreateRequest(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 31),
                1_000_000L
        );

        BacktestCreateResponse response = new BacktestCreateResponse(
                backtestId,
                strategyId,
                BacktestStatus.REQUESTED,
                Instant.parse("2026-09-06T00:00:00Z")
        );

        given(backtestCommandService.createBacktest(
                eq(userId),
                eq(strategyId),
                any(BacktestCreateRequest.class))
        ).willReturn(response);

        // when
        ResultActions result = mockMvc.perform(
                post("/api/v1/strategies/{strategyId}/backtests", strategyId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
        );

        // then
        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.backtestId").value(backtestId.toString()))
                .andExpect(jsonPath("$.data.status").value("REQUESTED"))
                .andExpect(jsonPath("$.data.strategyId").value(strategyId.toString()))
                .andExpect(jsonPath("$.data.requestedAt").exists());
    }

    @Test
    @DisplayName("백테스트 요청 대상 전략이 없으면 404를 반환한다.")
    void createBacktestStrategyNotFound() throws Exception{
        // given
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        BacktestCreateRequest request = new BacktestCreateRequest(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 31),
                 1_000_000L
        );

        given(backtestCommandService.createBacktest(
                eq(userId),
                eq(strategyId),
                any(BacktestCreateRequest.class)
        )).willThrow(new BusinessException(StrategyErrorCode.STRATEGY_NOT_FOUND));

        // when
        ResultActions result = mockMvc.perform(
                post("/api/v1/strategies/{strategyId}/backtests", strategyId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
        );

        // then
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));

    }
}