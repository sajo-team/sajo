package com.sajo.trading_service.ai_risk.service.command;

import com.sajo.trading_service.ai_risk.client.backtest.dto.BacktestInternalResponse;
import com.sajo.trading_service.ai_risk.client.strategy.dto.StrategyInternalResponse;
import com.sajo.trading_service.ai_risk.domain.AiAnalysisStatus;
import com.sajo.trading_service.ai_risk.domain.AiRiskAnalysis;
import com.sajo.trading_service.ai_risk.event.AiRiskAnalysisRequestedEvent;
import com.sajo.trading_service.ai_risk.repository.command.AiRiskAnalysisCommandRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("ai-risk")
@ExtendWith(MockitoExtension.class)
class AiRiskAnalysisPersistenceServiceTest {

    @Mock
    private AiRiskAnalysisCommandRepository repository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AiRiskAnalysisPersistenceService persistenceService;

    private UUID userId;
    private UUID strategyId;
    private UUID backtestId;

    private StrategyInternalResponse strategy;
    private BacktestInternalResponse backtest;

    private StrategyInternalResponse createStrategy() {
        return new StrategyInternalResponse(
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
    }

    private BacktestInternalResponse createBacktest() {
        return new BacktestInternalResponse(
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
    }

    @BeforeEach
    void setup(){
        userId = UUID.randomUUID();
        strategyId = UUID.randomUUID();
        backtestId = UUID.randomUUID();

        strategy = createStrategy();
        backtest = createBacktest();
    }

    @Test
    @DisplayName("PENDING 분석이 없으면 새로운 분석을 저장하고 이벤트를 발행한다.")
    void create_noPendingAnalysis_createAndPublishEvent(){
        when(repository.findByUserIdAndStrategyIdAndBacktestIdAndStatus(
                userId,
                strategyId,
                backtestId,
                AiAnalysisStatus.PENDING
        )).thenReturn(Optional.empty());

        when(repository.save(any(AiRiskAnalysis.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AiRiskAnalysis result = persistenceService.create(
                userId,
                strategyId,
                backtestId,
                strategy,
                backtest
        );

        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getStrategyId()).isEqualTo(strategyId);
        assertThat(result.getBacktestId()).isEqualTo(backtestId);
        assertThat(result.getStatus()).isEqualTo(AiAnalysisStatus.PENDING);

        verify(repository).save(any(AiRiskAnalysis.class));
        verify(eventPublisher).publishEvent(any(AiRiskAnalysisRequestedEvent.class));
    }

    @Test
    @DisplayName("PENDING 분석이 이미 있으면 기존 분석을 반환하고 새 분석을 저장하지 않는다")
    void create_pendingAnalysisExists_returnsExistingAnalysis() {
        AiRiskAnalysis existingAnalysis = AiRiskAnalysis.create(
                userId,
                strategyId,
                backtestId
        );

        when(repository.findByUserIdAndStrategyIdAndBacktestIdAndStatus(
                userId,
                strategyId,
                backtestId,
                AiAnalysisStatus.PENDING
        )).thenReturn(Optional.of(existingAnalysis));

        AiRiskAnalysis result = persistenceService.create(
                userId,
                strategyId,
                backtestId,
                strategy,
                backtest
        );

        assertThat(result).isSameAs(existingAnalysis);

        verify(repository, never())
                .save(any(AiRiskAnalysis.class));

        verify(eventPublisher, never())
                .publishEvent(any());
    }
}