package com.sajo.trading_service.ai_risk.service.command;

import com.sajo.trading_service.ai_risk.client.backtest.dto.BacktestInternalResponse;
import com.sajo.trading_service.ai_risk.client.strategy.dto.StrategyInternalResponse;
import com.sajo.trading_service.ai_risk.domain.AiAnalysisStatus;
import com.sajo.trading_service.ai_risk.domain.AiRiskAnalysis;
import com.sajo.trading_service.ai_risk.event.AiRiskAnalysisRequestedEvent;
import com.sajo.trading_service.ai_risk.repository.command.AiRiskAnalysisCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiRiskAnalysisPersistenceService {

    private final AiRiskAnalysisCommandRepository aiRiskAnalysisCommandRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public AiRiskAnalysis create(
            UUID userId,
            UUID strategyId,
            UUID backtestId,
            StrategyInternalResponse strategy,
            BacktestInternalResponse backtest
    ){
        Optional<AiRiskAnalysis> pendingAnalysis = aiRiskAnalysisCommandRepository.findByUserIdAndStrategyIdAndBacktestIdAndStatus(
                userId,
                strategyId,
                backtestId,
                AiAnalysisStatus.PENDING
        );

        if(pendingAnalysis.isPresent()){
            return pendingAnalysis.get();
        }

        AiRiskAnalysis analysis = AiRiskAnalysis.create(
                userId,
                strategyId,
                backtestId
        );

        AiRiskAnalysis savedAnalysis = aiRiskAnalysisCommandRepository.save(analysis);

        eventPublisher.publishEvent(
                new AiRiskAnalysisRequestedEvent(
                        savedAnalysis.getId(),
                        strategy,
                        backtest
                )
        );

        return savedAnalysis;
    }
}
