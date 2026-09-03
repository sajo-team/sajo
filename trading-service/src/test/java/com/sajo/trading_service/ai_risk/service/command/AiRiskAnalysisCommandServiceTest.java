package com.sajo.trading_service.ai_risk.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.common.response.GeneralResponse;
import com.sajo.trading_service.ai_risk.client.backtest.BacktestFeignClient;
import com.sajo.trading_service.ai_risk.client.backtest.dto.BacktestInternalResponse;
import com.sajo.trading_service.ai_risk.client.strategy.StrategyFeignClient;
import com.sajo.trading_service.ai_risk.client.strategy.dto.StrategyInternalResponse;
import com.sajo.trading_service.ai_risk.controller.dto.request.AiRiskAnalysisCreateRequest;
import com.sajo.trading_service.ai_risk.domain.AiRiskAnalysis;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("ai-risk")
@ExtendWith(MockitoExtension.class)
class AiRiskAnalysisCommandServiceTest {

    @Mock
    private StrategyFeignClient strategyFeignClient;

    @Mock
    private BacktestFeignClient backtestFeignClient;

    @Mock
    private AiRiskAnalysisPersistenceService persistenceService;

    @InjectMocks
    private AiRiskAnalysisCommandService commandService;

    private UUID userId;
    private UUID strategyId;
    private UUID backtestId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        strategyId = UUID.randomUUID();
        backtestId = UUID.randomUUID();
    }

    @Test
    @DisplayName("유효한 전략과 백테스트이면 AI 분석 생성을 요청한다")
    void create_success() {
        StrategyInternalResponse strategy =
                createStrategy(userId, strategyId);

        BacktestInternalResponse backtest =
                createBacktest(userId, strategyId, "COMPLETED");

        AiRiskAnalysis analysis =
                AiRiskAnalysis.create(userId, strategyId, backtestId);

        when(strategyFeignClient.getStrategy(strategyId))
                .thenReturn(new GeneralResponse<>(
                        true,
                        "조회 성공",
                        strategy
                ));

        when(backtestFeignClient.getBacktest(backtestId))
                .thenReturn(new GeneralResponse<>(
                        true,
                        "조회 성공",
                        backtest
                ));

        when(persistenceService.create(
                userId,
                strategyId,
                backtestId,
                strategy,
                backtest
        )).thenReturn(analysis);

        commandService.create(
                userId,
                new AiRiskAnalysisCreateRequest(strategyId, backtestId)
        );

        verify(persistenceService).create(
                userId,
                strategyId,
                backtestId,
                strategy,
                backtest
        );
    }

    @Test
    @DisplayName("다른 사용자의 전략이면 분석 요청에 실패한다")
    void create_strategyAccessDenied() {
        UUID otherUserId = UUID.randomUUID();

        StrategyInternalResponse strategy =
                createStrategy(otherUserId, strategyId);

        BacktestInternalResponse backtest =
                createBacktest(userId, strategyId, "COMPLETED");

        when(strategyFeignClient.getStrategy(strategyId))
                .thenReturn(new GeneralResponse<>(
                        true,
                        "조회 성공",
                        strategy
                ));

        when(backtestFeignClient.getBacktest(backtestId))
                .thenReturn(new GeneralResponse<>(
                        true,
                        "조회 성공",
                        backtest
                ));

        assertThatThrownBy(() ->
                commandService.create(
                        userId,
                        new AiRiskAnalysisCreateRequest(strategyId, backtestId)
                )
        ).isInstanceOf(BusinessException.class);

        verify(persistenceService, never())
                .create(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("다른 사용자의 백테스트이면 분석 요청에 실패한다")
    void create_backtestAccessDenied() {
        UUID otherUserId = UUID.randomUUID();

        StrategyInternalResponse strategy =
                createStrategy(userId, strategyId);

        BacktestInternalResponse backtest =
                createBacktest(otherUserId, strategyId, "COMPLETED");

        when(strategyFeignClient.getStrategy(strategyId))
                .thenReturn(new GeneralResponse<>(
                        true,
                        "조회 성공",
                        strategy
                ));

        when(backtestFeignClient.getBacktest(backtestId))
                .thenReturn(new GeneralResponse<>(
                        true,
                        "조회 성공",
                        backtest
                ));

        assertThatThrownBy(() ->
                commandService.create(
                        userId,
                        new AiRiskAnalysisCreateRequest(strategyId, backtestId)
                )
        ).isInstanceOf(BusinessException.class);

        verify(persistenceService, never())
                .create(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("전략과 백테스트의 strategyId가 다르면 분석 요청에 실패한다")
    void create_strategyBacktestMismatch() {
        UUID differentStrategyId = UUID.randomUUID();

        StrategyInternalResponse strategy =
                createStrategy(userId, strategyId);

        BacktestInternalResponse backtest =
                createBacktest(
                        userId,
                        differentStrategyId,
                        "COMPLETED"
                );

        when(strategyFeignClient.getStrategy(strategyId))
                .thenReturn(new GeneralResponse<>(
                        true,
                        "조회 성공",
                        strategy
                ));

        when(backtestFeignClient.getBacktest(backtestId))
                .thenReturn(new GeneralResponse<>(
                        true,
                        "조회 성공",
                        backtest
                ));

        assertThatThrownBy(() ->
                commandService.create(
                        userId,
                        new AiRiskAnalysisCreateRequest(strategyId, backtestId)
                )
        ).isInstanceOf(BusinessException.class);

        verify(persistenceService, never())
                .create(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("완료되지 않은 백테스트이면 분석 요청에 실패한다")
    void create_backtestNotCompleted() {
        StrategyInternalResponse strategy =
                createStrategy(userId, strategyId);

        BacktestInternalResponse backtest =
                createBacktest(userId, strategyId, "RUNNING");

        when(strategyFeignClient.getStrategy(strategyId))
                .thenReturn(new GeneralResponse<>(
                        true,
                        "조회 성공",
                        strategy
                ));

        when(backtestFeignClient.getBacktest(backtestId))
                .thenReturn(new GeneralResponse<>(
                        true,
                        "조회 성공",
                        backtest
                ));

        assertThatThrownBy(() ->
                commandService.create(
                        userId,
                        new AiRiskAnalysisCreateRequest(strategyId, backtestId)
                )
        ).isInstanceOf(BusinessException.class);

        verify(persistenceService, never())
                .create(any(), any(), any(), any(), any());
    }

    private StrategyInternalResponse createStrategy(
            UUID strategyUserId,
            UUID responseStrategyId
    ) {
        return new StrategyInternalResponse(
                responseStrategyId,
                strategyUserId,
                "005930",
                "테스트 전략",
                70_000L,
                80_000L,
                new BigDecimal("5.0"),
                new BigDecimal("10.0"),
                1_000_000L,
                100_000L,
                new BigDecimal("15.0"),
                new BigDecimal("1.5"),
                new BigDecimal("10.0"),
                "ACTIVE"
        );
    }

    private BacktestInternalResponse createBacktest(
            UUID backtestUserId,
            UUID responseStrategyId,
            String status
    ) {
        return new BacktestInternalResponse(
                backtestId,
                responseStrategyId,
                backtestUserId,
                "005930",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 6, 30),
                1_000_000L,
                status,
                new BigDecimal("12.5"),
                new BigDecimal("8.3"),
                new BigDecimal("60.0"),
                30,
                3
        );
    }
}