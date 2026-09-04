package com.sajo.trading_service.trading.controller;
 
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajo.common.exception.BusinessException;
import com.sajo.common.exception.GlobalExceptionHandler;
import com.sajo.trading_service.trading.controller.dto.request.TradingLimitCreateRequest;
import com.sajo.trading_service.trading.controller.dto.request.TradingLimitUpdateRequest;
import com.sajo.trading_service.trading.controller.dto.response.TradingLimitCreateResponse;
import com.sajo.trading_service.trading.controller.dto.response.TradingLimitQueryResponse;
import com.sajo.trading_service.trading.controller.dto.response.TradingLimitUpdateResponse;
import com.sajo.trading_service.trading.exception.TradingErrorCode;
import com.sajo.trading_service.trading.service.command.TradingLimitCommandService;
import com.sajo.trading_service.trading.service.query.TradingLimitQueryService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
 
@WebMvcTest(TradingLimitController.class)
@Import(GlobalExceptionHandler.class)
class TradingLimitControllerTest {
 
    @Autowired
    private MockMvc mockMvc;
 
    @Autowired
    private ObjectMapper objectMapper;
 
    @MockitoBean
    private TradingLimitCommandService tradingLimitCommandService;
 
    @MockitoBean
    private TradingLimitQueryService tradingLimitQueryService;
 
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
                                .header("X-User-Id", userId.toString())
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
                                .header("X-User-Id", userId.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
 
    @Test
    @DisplayName("자동매매 공통 한도를 조회하면 200을 반환한다")
    void getTradingLimit() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tradingLimitId = UUID.randomUUID();
 
        TradingLimitQueryResponse response =
                new TradingLimitQueryResponse(
                        tradingLimitId,
                        3_000_000L,
                        10,
                        new BigDecimal("5.00"),
                        Instant.now(),
                        Instant.now()
                );
 
        given(tradingLimitQueryService.findByUserId(userId))
                .willReturn(response);
 
        mockMvc.perform(
                        get("/api/v1/trading-limits")
                                .header("X-User-Id", userId.toString())
                )
                .andExpect(status().isOk())
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
    @DisplayName("자동매매 공통 한도가 없으면 404를 반환한다")
    void getTradingLimitNotFound() throws Exception {
        UUID userId = UUID.randomUUID();
 
        given(tradingLimitQueryService.findByUserId(userId))
                .willThrow(new BusinessException(
                        TradingErrorCode.TRADING_LIMIT_NOT_FOUND
                ));
 
        mockMvc.perform(
                        get("/api/v1/trading-limits")
                                .header("X-User-Id", userId.toString())
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode")
                        .value("AUTO_TRADING_0003"));
    }
 
    @Test
    @DisplayName("자동매매 공통 한도를 수정하면 200을 반환한다")
    void updateTradingLimit() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID tradingLimitId = UUID.randomUUID();
 
        TradingLimitUpdateRequest request =
                new TradingLimitUpdateRequest(
                        5_000_000L,
                        null,
                        null
                );
 
        TradingLimitUpdateResponse response =
                new TradingLimitUpdateResponse(
                        tradingLimitId,
                        5_000_000L,
                        10,
                        new BigDecimal("5.00"),
                        Instant.now()
                );
 
        given(tradingLimitCommandService.updateTradingLimit(
                eq(userId),
                any(TradingLimitUpdateRequest.class)
        )).willReturn(response);
 
        // when & then
        mockMvc.perform(
                        patch("/api/v1/trading-limits")
                                .header("X-User-Id", userId.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(request)
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(
                        jsonPath("$.data.tradingLimitId")
                                .value(tradingLimitId.toString())
                )
                .andExpect(
                        jsonPath("$.data.dailyMaxOrderAmount")
                                .value(5_000_000)
                )
                .andExpect(
                        jsonPath("$.data.dailyMaxOrderCount")
                                .value(10)
                )
                .andExpect(
                        jsonPath("$.data.dailyLossLimitRate")
                                .value(5.00)
                );
    }
 
    @Test
    @DisplayName("수정 요청의 일일 최대 주문 금액이 0 이하이면 400을 반환한다")
    void updateTradingLimitInvalidOrderAmount() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
 
        TradingLimitUpdateRequest request =
                new TradingLimitUpdateRequest(
                        0L,
                        null,
                        null
                );
 
        // when & then
        mockMvc.perform(
                        patch("/api/v1/trading-limits")
                                .header("X-User-Id", userId.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(request)
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
 
    @Test
    @DisplayName("자동매매 공통 한도가 없으면 수정 시 404를 반환한다")
    void updateTradingLimitNotFound() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
 
        TradingLimitUpdateRequest request =
                new TradingLimitUpdateRequest(
                        5_000_000L,
                        null,
                        null
                );
 
        given(tradingLimitCommandService.updateTradingLimit(
                eq(userId),
                any(TradingLimitUpdateRequest.class)
        )).willThrow(
                new BusinessException(
                        TradingErrorCode.TRADING_LIMIT_NOT_FOUND
                )
        );
 
        // when & then
        mockMvc.perform(
                        patch("/api/v1/trading-limits")
                                .header("X-User-Id", userId.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(request)
                                )
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(
                        jsonPath("$.errorCode")
                                .value("AUTO_TRADING_0003")
                );
    }
 
    @Test
    @DisplayName("X-User-Id 헤더 없이 요청하면 401을 반환한다 (Gateway를 거치지 않은 요청)")
    void getTradingLimit_withoutUserIdHeader() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/trading-limits"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("COMMON_0002"));
    }
}
