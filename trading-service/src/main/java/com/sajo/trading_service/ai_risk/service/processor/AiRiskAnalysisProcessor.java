package com.sajo.trading_service.ai_risk.service.processor;

import com.sajo.trading_service.ai_risk.client.backtest.dto.BacktestInternalResponse;
import com.sajo.trading_service.ai_risk.client.strategy.dto.StrategyInternalResponse;
import com.sajo.trading_service.ai_risk.domain.AiAnalysisFailureType;
import com.sajo.trading_service.ai_risk.event.AiRiskAnalysisRequestedEvent;
import com.sajo.trading_service.ai_risk.exception.AiAnalysisException;
import com.sajo.trading_service.ai_risk.exception.AiResponseValidationException;
import com.sajo.trading_service.ai_risk.service.analysis.AiRiskAnalyzer;
import com.sajo.trading_service.ai_risk.service.analysis.AiRiskResponseValidator;
import com.sajo.trading_service.ai_risk.service.analysis.dto.AiRiskAnalysisResult;
import com.sajo.trading_service.ai_risk.service.command.AiRiskAnalysisResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiRiskAnalysisProcessor {

    private final AiRiskAnalyzer aiRiskAnalyzer;
    private final AiRiskAnalysisResultService aiRiskAnalysisResultService;
    private final AiRiskResponseValidator responseValidator;

    @Async("aiAnalysisExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void process(AiRiskAnalysisRequestedEvent event){

        try{
            AiRiskAnalysisResult result = aiRiskAnalyzer.analyze(
                    event.strategy(),
                    event.backtest()
            );

            responseValidator.validate(result);

            aiRiskAnalysisResultService.complete(
                    event.analysisId(),
                    result.riskLevel(),
                    result.summary(),
                    result.riskFactors(),
                    result.reasoning(),
                    result.recommendations()
            );
        } catch (AiResponseValidationException e){

            aiRiskAnalysisResultService.fail(
                    event.analysisId(),
                    AiAnalysisFailureType.VALIDATION_ERROR,
                    e.getMessage()
            );
        } catch (AiAnalysisException e){
            aiRiskAnalysisResultService.fail(
                    event.analysisId(),
                    e.getFailureType(),
                    e.getMessage()
            );
        } catch (Exception e){
            aiRiskAnalysisResultService.fail(
                    event.analysisId(),
                    AiAnalysisFailureType.LLM_API_ERROR,
                    e.getMessage()
            );
        }
    }
}
