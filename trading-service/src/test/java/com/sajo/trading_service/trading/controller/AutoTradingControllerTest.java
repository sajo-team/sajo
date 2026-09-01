package com.sajo.trading_service.trading.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajo.common.exception.GlobalExceptionHandler;
import com.sajo.trading_service.trading.controller.dto.request.AutoTradingCreateRequest;
import com.sajo.trading_service.trading.controller.dto.response.AutoTradingCreateResponse;
import com.sajo.trading_service.trading.service.command.AutoTradingCommandService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AutoTradingController.class)
@Import(GlobalExceptionHandler.class)
class AutoTradingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AutoTradingCommandService autoTradingCommandService;

    @Test
    @DisplayName("전략별 자동매매 설정을 생성하면 201을 반환한다")
    void createAutoTrading() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();
        UUID autoTradingId = UUID.randomUUID();

        AutoTradingCreateRequest request =
                new AutoTradingCreateRequest(strategyId);

        AutoTradingCreateResponse response =
                new AutoTradingCreateResponse(
                        autoTradingId,
                        strategyId,
                        true,
                        Instant.now()
                );

        given(autoTradingCommandService.createAutoTrading(
                eq(userId),
                any(AutoTradingCreateRequest.class)
        )).willReturn(response);

        // when & then
        mockMvc.perform(
                        post("/api/v1/auto-tradings")
                                .param("userId", userId.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(request)
                                )
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.autoTradingId")
                        .value(autoTradingId.toString()))
                .andExpect(jsonPath("$.data.strategyId")
                        .value(strategyId.toString()))
                .andExpect(jsonPath("$.data.enabled")
                        .value(true));
    }

    @Test
    @DisplayName("strategyId가 없으면 자동매매 설정 생성 시 400을 반환한다")
    void createAutoTradingWithoutStrategyId() throws Exception {
        // given
        AutoTradingCreateRequest request =
                new AutoTradingCreateRequest(null);

        // when & then
        mockMvc.perform(
                        post("/api/v1/auto-tradings")
                                .param("userId", UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(request)
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}