package com.sajo.trading_service.ai_risk.service.processor;

import com.sajo.trading_service.ai_risk.client.backtest.dto.BacktestInternalResponse;
import com.sajo.trading_service.ai_risk.client.strategy.dto.StrategyInternalResponse;
import com.sajo.trading_service.ai_risk.document.AiAnalysisHistory;
import com.sajo.trading_service.ai_risk.domain.AiAnalysisFailureType;
import com.sajo.trading_service.ai_risk.domain.AiValidationType;
import com.sajo.trading_service.ai_risk.event.AiRiskAnalysisRequestedEvent;
import com.sajo.trading_service.ai_risk.exception.AiAnalysisException;
import com.sajo.trading_service.ai_risk.exception.AiResponseParseException;
import com.sajo.trading_service.ai_risk.exception.AiResponseValidationException;
import com.sajo.trading_service.ai_risk.repository.command.AiAnalysisHistoryCommandRepository;
import com.sajo.trading_service.ai_risk.service.analysis.AiRiskAnalyzer;
import com.sajo.trading_service.ai_risk.service.analysis.AiRiskResponseValidator;
import com.sajo.trading_service.ai_risk.service.analysis.dto.AiRiskAnalysisOutput;
import com.sajo.trading_service.ai_risk.service.analysis.dto.AiRiskAnalysisResult;
import com.sajo.trading_service.ai_risk.service.command.AiRiskAnalysisResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiRiskAnalysisProcessor {

    private final AiRiskAnalyzer aiRiskAnalyzer;
    private final AiRiskAnalysisResultService aiRiskAnalysisResultService;
    private final AiRiskResponseValidator responseValidator;
    private final AiAnalysisHistoryCommandRepository historyCommandRepository;

    private void saveSuccessHistory(
            AiRiskAnalysisRequestedEvent event,
            AiRiskAnalysisOutput output
    ){
        AiAnalysisHistory history = AiAnalysisHistory.builder()
                .analysisId(event.analysisId())
                .userId(event.strategy().userId())
                .strategyId(event.strategy().strategyId())
                .backtestId(event.backtest().backtestId())
                .requestSnapshot(Map.of(
                        "strategy", event.strategy(),
                        "backtest", event.backtest()
                ))
                .prompt(new AiAnalysisHistory.PromptSnapshot(
                        output.promptVersion(),
                        output.promptContent()
                ))
                .response(new AiAnalysisHistory.ResponseSnapshot(
                        output.rawResponse()
                ))
                .validation(new AiAnalysisHistory.ValidationSnapshot(
                        true,
                        true,
                        List.of()
                ))
                .metadata(new AiAnalysisHistory.MetadataSnapshot(
                        output.model(),
                        output.latencyMs()
                ))
                .build();

        saveHistorySafely(history);
    }

    private void saveValidationFailureHistory(
            AiRiskAnalysisRequestedEvent event,
            AiRiskAnalysisOutput output,
            AiResponseValidationException exception
    ){
        if(output == null){
            return;
        }

        boolean structureValid = exception.getValidationType() != AiValidationType.STRUCTURE;
        boolean contentValid = structureValid && exception.getValidationType() != AiValidationType.CONTENT;

        AiAnalysisHistory history = AiAnalysisHistory.builder()
                .analysisId(event.analysisId())
                .userId(event.strategy().userId())
                .strategyId(event.strategy().strategyId())
                .backtestId(event.backtest().backtestId())
                .requestSnapshot(Map.of(
                        "strategy", event.strategy(),
                        "backtest", event.backtest()
                ))
                .prompt(new AiAnalysisHistory.PromptSnapshot(
                        output.promptVersion(),
                        output.promptContent()
                ))
                .response(new AiAnalysisHistory.ResponseSnapshot(
                        output.rawResponse()
                ))
                .validation(new AiAnalysisHistory.ValidationSnapshot(
                        structureValid,
                        contentValid,
                        List.of(exception.getMessage())
                ))
                .metadata(new AiAnalysisHistory.MetadataSnapshot(
                        output.model(),
                        output.latencyMs()
                ))
                .build();

        saveHistorySafely(history);
    }

    private void saveParseFailureHistory(
            AiRiskAnalysisRequestedEvent event,
            AiResponseParseException exception
    ){
        AiAnalysisHistory history = AiAnalysisHistory.builder()
                .analysisId(event.analysisId())
                .userId(event.strategy().userId())
                .strategyId(event.strategy().strategyId())
                .backtestId(event.backtest().backtestId())
                .requestSnapshot(Map.of(
                        "strategy", event.strategy(),
                        "backtest", event.backtest()
                ))
                .response(new AiAnalysisHistory.ResponseSnapshot(
                        exception.getRawResponse()
                ))
                .validation(new AiAnalysisHistory.ValidationSnapshot(
                        false,
                        false,List.of(exception.getMessage())
                ))
                .build();

        saveHistorySafely(history);
    }

    private void saveHistorySafely(AiAnalysisHistory history){
        try{
            historyCommandRepository.save(history);
        } catch(Exception e){
            //TODO : Mongo Audit 저장 실패 로깅 및 모니터링 추가
        }
    }

    private void saveLlmFailureHistory(
            AiRiskAnalysisRequestedEvent event,
            AiAnalysisException exception
    ){
        AiAnalysisHistory history = AiAnalysisHistory.builder()
                .analysisId(event.analysisId())
                .userId(event.strategy().userId())
                .strategyId(event.strategy().strategyId())
                .backtestId(event.backtest().backtestId())
                .requestSnapshot(Map.of(
                        "strategy", event.strategy(),
                        "backtest", event.backtest()
                ))
                .validation(new AiAnalysisHistory.ValidationSnapshot(
                        false,
                        false,
                        List.of(exception.getMessage())
                ))
                .build();

        saveHistorySafely(history);
    }

    @Async("aiAnalysisExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void process(AiRiskAnalysisRequestedEvent event){

        AiRiskAnalysisOutput output = null;

        try{
            output = aiRiskAnalyzer.analyze(
                    event.strategy(),
                    event.backtest()
            );

            AiRiskAnalysisResult result = output.result();

            responseValidator.validate(result);

            aiRiskAnalysisResultService.complete(
                    event.analysisId(),
                    result.riskLevel(),
                    result.summary(),
                    result.riskFactors(),
                    result.reasoning(),
                    result.recommendations()
            );

            saveSuccessHistory(event, output);

        } catch (AiResponseValidationException e){

            aiRiskAnalysisResultService.fail(
                    event.analysisId(),
                    AiAnalysisFailureType.VALIDATION_ERROR,
                    e.getMessage()
            );

            saveValidationFailureHistory(event, output, e);

        } catch (AiResponseParseException e){
            aiRiskAnalysisResultService.fail(
                    event.analysisId(),
                    AiAnalysisFailureType.RESPONSE_PARSE_ERROR,
                    e.getMessage()
            );

            saveParseFailureHistory(event, e);

        } catch (AiAnalysisException e){
            aiRiskAnalysisResultService.fail(
                    event.analysisId(),
                    e.getFailureType(),
                    e.getMessage()
            );

            saveLlmFailureHistory(event, e);
        } catch (Exception e){
            aiRiskAnalysisResultService.fail(
                    event.analysisId(),
                    AiAnalysisFailureType.INTERNAL_ERROR,
                    e.getMessage()
            );
        }
    }
}
