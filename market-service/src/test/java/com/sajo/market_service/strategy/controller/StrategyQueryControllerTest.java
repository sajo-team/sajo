package com.sajo.market_service.strategy.controller;

import com.sajo.common.exception.GlobalExceptionHandler;
import com.sajo.market_service.strategy.controller.dto.response.StrategyDetailResponse;
import com.sajo.market_service.strategy.controller.dto.response.StrategyListResponse;
import com.sajo.market_service.strategy.controller.dto.response.StrategySummaryResponse;
import com.sajo.market_service.strategy.domain.StrategyStatus;
import com.sajo.market_service.strategy.service.query.StrategyQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StrategyQueryController.class)
@Import(GlobalExceptionHandler.class)
class StrategyQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StrategyQueryService strategyQueryService;

    @Test
    @DisplayName("전략 목록을 조회하면 200과 목록을 반환한다")
    void getStrategies() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        StrategyListResponse response = new StrategyListResponse(
                List.of(new StrategySummaryResponse(
                        strategyId, "삼성전자 눌림목 전략", "005930",
                        StrategyStatus.INACTIVE, 3_000_000L
                )),
                0, 20, 1
        );

        given(strategyQueryService.getStrategies(eq(userId), isNull(), isNull(), any(Pageable.class)))
                .willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/strategies").header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.strategies[0].strategyId").value(strategyId.toString()))
                .andExpect(jsonPath("$.data.strategies[0].stockCode").value("005930"))
                .andExpect(jsonPath("$.data.strategies[0].status").value("INACTIVE"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("status/stockCode 파라미터를 바인딩하여 조회한다")
    void getStrategiesWithFilters() throws Exception {
        // given
        UUID userId = UUID.randomUUID();

        given(strategyQueryService.getStrategies(eq(userId), eq(StrategyStatus.ACTIVE), eq("005930"), any(Pageable.class)))
                .willReturn(new StrategyListResponse(List.of(), 0, 20, 0));

        // when & then
        mockMvc.perform(get("/api/v1/strategies")
                        .header("X-User-Id", userId.toString())
                        .param("status", "ACTIVE")
                        .param("stockCode", "005930"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.strategies").isEmpty());
    }

    @Test
    @DisplayName("페이징 파라미터를 바인딩하여 조회한다")
    void getStrategiesWithPaging() throws Exception {
        // given
        UUID userId = UUID.randomUUID();

        given(strategyQueryService.getStrategies(eq(userId), isNull(), isNull(), any(Pageable.class)))
                .willReturn(new StrategyListResponse(List.of(), 1, 5, 0));

        // when & then
        mockMvc.perform(get("/api/v1/strategies")
                        .header("X-User-Id", userId.toString())
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(5));
    }

    @Test
    @DisplayName("X-User-Id 헤더가 없으면 요청이 실패한다")
    // GlobalExceptionHandler(libs:common)가 MissingServletRequestParameterException을
    // 별도로 처리하지 않아 500으로 내려가는 현재 동작을 그대로 검증한다.
    // 원래는 400이 맞으므로, GlobalExceptionHandler에 핸들러 추가가 필요하다(별도 이슈 대상).
    void getStrategiesWithoutUserHeader() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/strategies"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("전략 상세를 조회하면 200과 상세 정보를 반환한다.")
    void getStrategy() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        StrategyDetailResponse response = new StrategyDetailResponse(
                strategyId,
                "005930",
                "삼성전자 눌림목 전략",
                70_000L,
                80_000L,
                new BigDecimal("5.0000"),
                new BigDecimal("10.0000"),
                3_000_000L,
                new BigDecimal("15.0000"),
                new BigDecimal("1.2000"),
                new BigDecimal("10.0000"),
                StrategyStatus.INACTIVE
        );

        given(strategyQueryService.getStrategy(userId, strategyId))
                .willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/strategies/{strategyId}", strategyId)
                .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.strategyId").value(strategyId.toString()))
                .andExpect(jsonPath("$.data.stockCode").value("005930"))
                .andExpect(jsonPath("$.data.strategyName").value("삼성전자 눌림목 전략"))
                .andExpect(jsonPath("$.data.buyConditionPrice").value(70000))
                .andExpect(jsonPath("$.data.sellConditionPrice").value(80000))
                .andExpect(jsonPath("$.data.stopLossRate").value(5.0000))
                .andExpect(jsonPath("$.data.targetReturnRate").value(10.0000))
                .andExpect(jsonPath("$.data.allocatedAmount").value(3000000))
                .andExpect(jsonPath("$.data.perCondition").value(15.0000))
                .andExpect(jsonPath("$.data.pbrCondition").value(1.2000))
                .andExpect(jsonPath("$.data.roeCondition").value(10.0000))
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));
    }
}
