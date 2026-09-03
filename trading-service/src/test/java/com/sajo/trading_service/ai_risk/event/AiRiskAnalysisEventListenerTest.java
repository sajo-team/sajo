package com.sajo.trading_service.ai_risk.event;

import com.sajo.trading_service.ai_risk.client.backtest.dto.BacktestInternalResponse;
import com.sajo.trading_service.ai_risk.client.strategy.dto.StrategyInternalResponse;
import com.sajo.trading_service.ai_risk.domain.AiAnalysisFailureType;
import com.sajo.trading_service.ai_risk.service.command.AiRiskAnalysisResultService;
import com.sajo.trading_service.ai_risk.service.processor.AiRiskAnalysisAsyncProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("ai-risk")
@ExtendWith(MockitoExtension.class)
class AiRiskAnalysisEventListenerTest {

    @Mock
    private AiRiskAnalysisAsyncProcessor asyncProcessor;

    @Mock
    private AiRiskAnalysisResultService resultService;

    @InjectMocks
    private AiRiskAnalysisEventListener eventListener;

    private UUID analysisId;
    private AiRiskAnalysisRequestedEvent event;

    @BeforeEach
    void setUp() {
        analysisId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();
        UUID backtestId = UUID.randomUUID();

        StrategyInternalResponse strategy =
                new StrategyInternalResponse(
                        strategyId,
                        userId,
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

        BacktestInternalResponse backtest =
                new BacktestInternalResponse(
                        backtestId,
                        strategyId,
                        userId,
                        "005930",
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 6, 30),
                        1_000_000L,
                        "COMPLETED",
                        new BigDecimal("12.5"),
                        new BigDecimal("8.3"),
                        new BigDecimal("60.0"),
                        30,
                        3
                );

        event = new AiRiskAnalysisRequestedEvent(
                analysisId,
                strategy,
                backtest
        );
    }

    @Test
    @DisplayName("AI 분석 이벤트를 수신하면 비동기 Processor에 작업을 전달한다.")
    void handle_success(){
        eventListener.handle(event);

        verify(asyncProcessor).process(event);

        verifyNoInteractions(resultService);
    }

    @Test
    @DisplayName("비동기 작업 제출이 거부되면 분석을 INTERNAL_ERROR로 실패 처리한다")
    void handle_taskRejected_failsAnalysis() {
        doThrow(new TaskRejectedException("executor queue full"))
                .when(asyncProcessor)
                .process(event);

        eventListener.handle(event);

        verify(asyncProcessor).process(event);

        verify(resultService).fail(
                analysisId,
                AiAnalysisFailureType.INTERNAL_ERROR,
                "AI 분석 작업 실행이 거부되었습니다."
        );
    }

}