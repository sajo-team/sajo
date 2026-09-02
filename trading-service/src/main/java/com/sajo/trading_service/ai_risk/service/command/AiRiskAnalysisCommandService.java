package com.sajo.trading_service.ai_risk.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.ai_risk.client.backtest.dto.BacktestInternalResponse;
import com.sajo.trading_service.ai_risk.client.strategy.dto.StrategyInternalResponse;
import com.sajo.trading_service.ai_risk.client.backtest.BacktestFeignClient;
import com.sajo.trading_service.ai_risk.client.strategy.StrategyFeignClient;
import com.sajo.trading_service.ai_risk.controller.dto.request.AiRiskAnalysisCreateRequest;
import com.sajo.trading_service.ai_risk.controller.dto.response.AiRiskAnalysisCreateResponse;
import com.sajo.trading_service.ai_risk.domain.AiRiskAnalysis;
import com.sajo.trading_service.ai_risk.event.AiRiskAnalysisRequestedEvent;
import com.sajo.trading_service.ai_risk.exception.AiRiskErrorCode;
import com.sajo.trading_service.ai_risk.repository.command.AiRiskAnalysisCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiRiskAnalysisCommandService {

    private final StrategyFeignClient strategyFeignClient;
    private final BacktestFeignClient backtestFeignClient;
    private final AiRiskAnalysisPersistenceService persistenceService;

    private void validateAnalysisRequest(
            UUID userId,
            StrategyInternalResponse strategy,
            BacktestInternalResponse backtest
    ){
        if(!userId.equals(strategy.userId())){
            throw new BusinessException(AiRiskErrorCode.STRATEGY_ACCESS_DENIED);
        }

        if(!userId.equals(backtest.userId())){
            throw new BusinessException(AiRiskErrorCode.BACKTEST_ACCESS_DENIED);
        }

        if(!strategy.strategyId().equals(backtest.strategyId())){
            throw new BusinessException(AiRiskErrorCode.STRATEGY_BACKTEST_MISMATCH);
        }

        if(!"COMPLETED".equals(backtest.backtestStatus())){
            throw new BusinessException(AiRiskErrorCode.BACKTEST_NOT_COMPLETED);
        }
    }

    public AiRiskAnalysisCreateResponse create(
            UUID userId,
            AiRiskAnalysisCreateRequest request
    ) {
        StrategyInternalResponse strategy = strategyFeignClient.getStrategy(request.strategyId());

        BacktestInternalResponse backtest = backtestFeignClient.getBacktest(request.backtestId());

        validateAnalysisRequest(userId, strategy, backtest);

        //여기서부터 짧은 DB Transaction

        AiRiskAnalysis analysis = persistenceService.create(
                userId,
                request.strategyId(),
                request.backtestId(),
                strategy,
                backtest
        );

        return AiRiskAnalysisCreateResponse.from(analysis);
    }
}
