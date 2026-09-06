package com.sajo.market_service.strategy.controller;

import com.sajo.common.exception.BusinessException;
import com.sajo.common.exception.GlobalExceptionHandler;
import com.sajo.market_service.strategy.controller.dto.response.BacktestDetailResponse;
import com.sajo.market_service.strategy.controller.dto.response.BacktestInternalResponse;
import com.sajo.market_service.strategy.controller.dto.response.BacktestListResponse;
import com.sajo.market_service.strategy.controller.dto.response.BacktestStatusResponse;
import com.sajo.market_service.strategy.controller.dto.response.BacktestSummaryResponse;
import com.sajo.market_service.strategy.domain.BacktestStatus;
import com.sajo.market_service.strategy.exception.StrategyErrorCode;
import com.sajo.market_service.strategy.service.query.BacktestQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({
        BacktestQueryController.class,
        BacktestInternalQueryController.class
})
@Import(GlobalExceptionHandler.class)
class BacktestQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BacktestQueryService backtestQueryService;

    @Test
    @DisplayName("백테스트 상태를 조회하면 200과 상태 정보를 반환한다")
    void getBacktestStatus() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();
        UUID backtestId = UUID.randomUUID();

        given(backtestQueryService.getBacktestStatus(userId, strategyId, backtestId))
                .willReturn(new BacktestStatusResponse(backtestId, BacktestStatus.REQUESTED));

        // when & then
        mockMvc.perform(get(
                        "/api/v1/strategies/{strategyId}/backtests/{backtestId}/status",
                        strategyId,
                        backtestId
                ).header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.backtestId").value(backtestId.toString()))
                .andExpect(jsonPath("$.data.status").value("REQUESTED"));
    }

    @Test
    @DisplayName("백테스트 상세를 조회하면 200과 상세 정보를 반환한다")
    void getBacktestDetail() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();
        UUID backtestId = UUID.randomUUID();

        BacktestDetailResponse response = new BacktestDetailResponse(
                backtestId,
                strategyId,
                "005930",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 31),
                1_000_000L,
                BacktestStatus.COMPLETED,
                new BigDecimal("8.2500"),
                new BigDecimal("-12.4000"),
                new BigDecimal("63.5000"),
                14,
                3,
                Instant.parse("2026-09-06T00:00:00Z")
        );

        given(backtestQueryService.getBacktestDetail(userId, strategyId, backtestId))
                .willReturn(response);

        // when & then
        mockMvc.perform(get(
                        "/api/v1/strategies/{strategyId}/backtests/{backtestId}",
                        strategyId,
                        backtestId
                ).header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.backtestId").value(backtestId.toString()))
                .andExpect(jsonPath("$.data.strategyId").value(strategyId.toString()))
                .andExpect(jsonPath("$.data.stockCode").value("005930"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.totalReturnRate").value(8.2500))
                .andExpect(jsonPath("$.data.mdd").value(-12.4000))
                .andExpect(jsonPath("$.data.winRate").value(63.5000))
                .andExpect(jsonPath("$.data.tradeCount").value(14))
                .andExpect(jsonPath("$.data.maxConsecutiveLosses").value(3));
    }

    @Test
    @DisplayName("백테스트 목록을 조회하면 200과 목록을 반환한다")
    void getBacktests() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();
        UUID backtestId = UUID.randomUUID();

        BacktestListResponse response = new BacktestListResponse(
                List.of(new BacktestSummaryResponse(
                        backtestId,
                        "005930",
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 3, 31),
                        BacktestStatus.REQUESTED,
                        null,
                        null,
                        Instant.parse("2026-09-06T00:00:00Z")
                )),
                0,
                20,
                1
        );

        given(backtestQueryService.getBacktests(eq(userId), eq(strategyId), any(Pageable.class)))
                .willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/strategies/{strategyId}/backtests", strategyId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.backtests[0].backtestId").value(backtestId.toString()))
                .andExpect(jsonPath("$.data.backtests[0].stockCode").value("005930"))
                .andExpect(jsonPath("$.data.backtests[0].status").value("REQUESTED"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("백테스트 조회 결과가 없으면 404를 반환한다")
    void getBacktestNotFound() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();
        UUID backtestId = UUID.randomUUID();

        given(backtestQueryService.getBacktestDetail(userId, strategyId, backtestId))
                .willThrow(new BusinessException(StrategyErrorCode.BACKTEST_NOT_FOUND));

        // when & then
        mockMvc.perform(get(
                        "/api/v1/strategies/{strategyId}/backtests/{backtestId}",
                        strategyId,
                        backtestId
                ).header("X-User-Id", userId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("내부 백테스트 조회 API는 순수 응답 DTO를 반환한다")
    void getBacktestInternal() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();
        UUID backtestId = UUID.randomUUID();

        BacktestInternalResponse response = new BacktestInternalResponse(
                backtestId,
                strategyId,
                userId,
                "005930",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 31),
                1_000_000L,
                BacktestStatus.COMPLETED,
                new BigDecimal("8.2500"),
                new BigDecimal("-12.4000"),
                new BigDecimal("63.5000"),
                14,
                3
        );

        given(backtestQueryService.getBacktestInternal(backtestId))
                .willReturn(response);

        // when & then
        mockMvc.perform(get("/internal/v1/backtests/{backtestId}", backtestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").doesNotExist())
                .andExpect(jsonPath("$.backtestId").value(backtestId.toString()))
                .andExpect(jsonPath("$.strategyId").value(strategyId.toString()))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.stockCode").value("005930"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }
}
