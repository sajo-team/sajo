package com.sajo.trading_service.trading.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajo.common.exception.BusinessException;
import com.sajo.common.exception.GlobalExceptionHandler;
import com.sajo.trading_service.trading.controller.dto.request.AutoTradingCreateRequest;
import com.sajo.trading_service.trading.controller.dto.request.AutoTradingUpdateRequest;
import com.sajo.trading_service.trading.controller.dto.response.AutoTradingCreateResponse;
import com.sajo.trading_service.trading.controller.dto.response.AutoTradingQueryResponse;
import com.sajo.trading_service.trading.controller.dto.response.AutoTradingUpdateResponse;
import com.sajo.trading_service.trading.exception.TradingErrorCode;
import com.sajo.trading_service.trading.service.command.AutoTradingCommandService;
import com.sajo.trading_service.trading.service.query.AutoTradingQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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

    @MockitoBean
    private AutoTradingQueryService autoTradingQueryService;

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

    @Test
    @DisplayName("자동매매 설정의 활성 상태를 수정하면 200을 반환한다")
    void updateAutoTrading() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID autoTradingId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        AutoTradingUpdateRequest request =
                new AutoTradingUpdateRequest(false);

        AutoTradingUpdateResponse response =
                new AutoTradingUpdateResponse(
                        autoTradingId,
                        strategyId,
                        false,
                        Instant.now()
                );

        given(autoTradingCommandService.updateAutoTrading(
                eq(userId),
                eq(autoTradingId),
                any(AutoTradingUpdateRequest.class)
        )).willReturn(response);

        mockMvc.perform(
                        patch("/api/v1/auto-tradings/{autoTradingId}", autoTradingId)
                                .param("userId", userId.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.autoTradingId")
                        .value(autoTradingId.toString()))
                .andExpect(jsonPath("$.data.strategyId")
                        .value(strategyId.toString()))
                .andExpect(jsonPath("$.data.enabled")
                        .value(false));
    }

    @Test
    @DisplayName("자동매매 설정이 없으면 수정 시 404를 반환한다")
    void updateAutoTradingNotFound() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID autoTradingId = UUID.randomUUID();

        AutoTradingUpdateRequest request =
                new AutoTradingUpdateRequest(false);

        given(autoTradingCommandService.updateAutoTrading(
                eq(userId),
                eq(autoTradingId),
                any(AutoTradingUpdateRequest.class)
        )).willThrow(
                new BusinessException(
                        TradingErrorCode.AUTO_TRADING_NOT_FOUND
                )
        );

        mockMvc.perform(
                        patch("/api/v1/auto-tradings/{autoTradingId}", autoTradingId)
                                .param("userId", userId.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode")
                        .value("AUTO_TRADING_0008"));
    }

    @Test
    @DisplayName("enabled 값이 없으면 자동매매 설정 수정 시 400을 반환한다")
    void updateAutoTradingWithoutEnabled() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID autoTradingId = UUID.randomUUID();

        AutoTradingUpdateRequest request =
                new AutoTradingUpdateRequest(null);

        mockMvc.perform(
                        patch("/api/v1/auto-tradings/{autoTradingId}", autoTradingId)
                                .param("userId", userId.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("자동매매 설정 목록 조회에 성공한다")
    void getAllAutoTradings_success() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID autoTradingId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        AutoTradingQueryResponse response =
                new AutoTradingQueryResponse(
                        autoTradingId,
                        strategyId,
                        true,
                        Instant.now(),
                        Instant.now()
                );

        when(autoTradingQueryService.findAllByUserId(
                eq(userId),
                any()
        )).thenReturn(
                new PageImpl<>(List.of(response))
        );

        // when & then
        mockMvc.perform(
                        get("/api/v1/auto-tradings")
                                .param("userId", userId.toString())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(
                        jsonPath("$.data.content[0].autoTradingId")
                                .value(autoTradingId.toString())
                )
                .andExpect(
                        jsonPath("$.data.content[0].strategyId")
                                .value(strategyId.toString())
                )
                .andExpect(
                        jsonPath("$.data.content[0].enabled")
                                .value(true)
                );
    }

    @Test
    @DisplayName("자동매매 설정 단건 조회에 성공한다")
    void getAutoTrading_success() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID autoTradingId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        AutoTradingQueryResponse response =
                new AutoTradingQueryResponse(
                        autoTradingId,
                        strategyId,
                        true,
                        Instant.now(),
                        Instant.now()
                );

        when(autoTradingQueryService.findById(
                autoTradingId,
                userId
        )).thenReturn(response);

        // when & then
        mockMvc.perform(
                        get(
                                "/api/v1/auto-tradings/{autoTradingId}",
                                autoTradingId
                        )
                                .param("userId", userId.toString())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(
                        jsonPath("$.data.autoTradingId")
                                .value(autoTradingId.toString())
                )
                .andExpect(
                        jsonPath("$.data.strategyId")
                                .value(strategyId.toString())
                )
                .andExpect(
                        jsonPath("$.data.enabled")
                                .value(true)
                );
    }

    @Test
    @DisplayName("자동매매 설정을 찾을 수 없으면 404를 반환한다")
    void getAutoTrading_notFound() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID autoTradingId = UUID.randomUUID();

        when(autoTradingQueryService.findById(
                autoTradingId,
                userId
        )).thenThrow(
                new BusinessException(
                        TradingErrorCode.AUTO_TRADING_NOT_FOUND
                )
        );

        // when & then
        mockMvc.perform(
                        get(
                                "/api/v1/auto-tradings/{autoTradingId}",
                                autoTradingId
                        )
                                .param("userId", userId.toString())
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(
                        jsonPath("$.errorCode")
                                .value("AUTO_TRADING_0008")
                );
    }
}