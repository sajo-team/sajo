package com.sajo.trading_service.ai_risk.service.processor;

import com.sajo.trading_service.ai_risk.client.backtest.dto.BacktestInternalResponse;
import com.sajo.trading_service.ai_risk.client.strategy.dto.StrategyInternalResponse;
import com.sajo.trading_service.ai_risk.document.AiAnalysisHistory;
import com.sajo.trading_service.ai_risk.domain.AiAnalysisFailureType;
import com.sajo.trading_service.ai_risk.domain.AiAnalysisStatus;
import com.sajo.trading_service.ai_risk.domain.AiValidationType;
import com.sajo.trading_service.ai_risk.kafka.dto.AiRiskAnalysisRequestedEvent;
import com.sajo.trading_service.ai_risk.exception.AiAnalysisException;
import com.sajo.trading_service.ai_risk.exception.AiResponseParseException;
import com.sajo.trading_service.ai_risk.exception.AiResponseValidationException;
import com.sajo.trading_service.ai_risk.repository.command.AiAnalysisHistoryCommandRepository;
import com.sajo.trading_service.ai_risk.service.analysis.AiRiskAnalyzer;
import com.sajo.trading_service.ai_risk.service.analysis.AiRiskResponseValidator;
import com.sajo.trading_service.ai_risk.service.analysis.dto.AiRiskAnalysisOutput;
import com.sajo.trading_service.ai_risk.service.analysis.dto.AiRiskAnalysisResult;
import com.sajo.trading_service.ai_risk.service.command.AiRiskAnalysisResultService;
import com.sajo.trading_service.ai_risk.service.query.AiRiskAnalysisQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiRiskAnalysisProcessor {

    private final AiRiskAnalyzer aiRiskAnalyzer;
    private final AiRiskAnalysisResultService aiRiskAnalysisResultService;
    private final AiRiskAnalysisQueryService aiRiskAnalysisQueryService;
    private final AiRiskResponseValidator responseValidator;
    private final AiAnalysisHistoryCommandRepository historyCommandRepository;

    private UUID analysisId(AiRiskAnalysisRequestedEvent event){
        return event.payload().analysisId();
    }

    private StrategyInternalResponse strategy(AiRiskAnalysisRequestedEvent event){
        return event.payload().strategy();
    }

    private BacktestInternalResponse backtest(AiRiskAnalysisRequestedEvent event){
        return event.payload().backtest();
    }

    private void saveSuccessHistory(
            AiRiskAnalysisRequestedEvent event,
            AiRiskAnalysisOutput output
    ){
        AiAnalysisHistory history = AiAnalysisHistory.builder()
                .analysisId(analysisId(event))
                .userId(strategy(event).userId())
                .strategyId(strategy(event).strategyId())
                .backtestId(backtest(event).backtestId())
                .requestSnapshot(Map.of(
                        "strategy", strategy(event),
                        "backtest", backtest(event)
                ))
                .prompt(new AiAnalysisHistory.PromptSnapshot(
                        output.promptKey(),
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
                .result(new AiAnalysisHistory.ResultSnapshot(
                        AiAnalysisStatus.COMPLETED,
                        null
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
                .analysisId(analysisId(event))
                .userId(strategy(event).userId())
                .strategyId(strategy(event).strategyId())
                .backtestId(backtest(event).backtestId())
                .requestSnapshot(Map.of(
                        "strategy", strategy(event),
                        "backtest", backtest(event)
                ))
                .prompt(new AiAnalysisHistory.PromptSnapshot(
                        output.promptKey(),
                        output.promptVersion(),
                        output.promptContent()
                ))
                .response(new AiAnalysisHistory.ResponseSnapshot(
                        output.rawResponse()
                ))
                .validation(new AiAnalysisHistory.ValidationSnapshot(
                        structureValid,
                        contentValid,
                        errorMessages(exception)
                ))
                .metadata(new AiAnalysisHistory.MetadataSnapshot(
                        output.model(),
                        output.latencyMs()
                ))
                .result(new AiAnalysisHistory.ResultSnapshot(
                        AiAnalysisStatus.FAILED,
                        AiAnalysisFailureType.VALIDATION_ERROR
                ))
                .build();

        saveHistorySafely(history);
    }

    private void saveParseFailureHistory(
            AiRiskAnalysisRequestedEvent event,
            AiResponseParseException exception
    ){
        AiAnalysisHistory history = AiAnalysisHistory.builder()
                .analysisId(analysisId(event))
                .userId(strategy(event).userId())
                .strategyId(strategy(event).strategyId())
                .backtestId(backtest(event).backtestId())
                .requestSnapshot(Map.of(
                        "strategy", strategy(event),
                        "backtest", backtest(event)
                ))
                .prompt(new AiAnalysisHistory.PromptSnapshot(
                        exception.getPromptKey(),
                        exception.getPromptVersion(),
                        exception.getPromptContent()
                ))
                .metadata(new AiAnalysisHistory.MetadataSnapshot(
                        exception.getModel(),
                        exception.getLatencyMs()
                ))
                .response(new AiAnalysisHistory.ResponseSnapshot(
                        exception.getRawResponse()
                ))
                .validation(new AiAnalysisHistory.ValidationSnapshot(
                        false,
                        false,
                        errorMessages(exception)
                ))
                .result(new AiAnalysisHistory.ResultSnapshot(
                        AiAnalysisStatus.FAILED,
                        AiAnalysisFailureType.RESPONSE_PARSE_ERROR
                ))
                .build();

        saveHistorySafely(history);
    }

    private void saveHistorySafely(AiAnalysisHistory history){
        try{
            historyCommandRepository.save(history);
        } catch(Exception e){
            log.error(
                    "AI 분석 감사 이력 저장 실패. analysisId={}",
                    history.getAnalysisId(),
                    e
            );
            //TODO : 모니터링 추가
        }
    }

    private void saveLlmFailureHistory(
            AiRiskAnalysisRequestedEvent event,
            AiAnalysisException exception
    ){
        AiAnalysisHistory history = AiAnalysisHistory.builder()
                .analysisId(analysisId(event))
                .userId(strategy(event).userId())
                .strategyId(strategy(event).strategyId())
                .backtestId(backtest(event).backtestId())
                .requestSnapshot(Map.of(
                        "strategy", strategy(event),
                        "backtest", backtest(event)
                ))
                .prompt(new AiAnalysisHistory.PromptSnapshot(
                        exception.getPromptKey(),
                        exception.getPromptVersion(),
                        exception.getPromptContent()
                ))
                .validation(new AiAnalysisHistory.ValidationSnapshot(
                        false,
                        false,
                        errorMessages(exception)
                ))
                .metadata(new AiAnalysisHistory.MetadataSnapshot(
                        exception.getModel(),
                        exception.getLatencyMs()
                ))
                .result(new AiAnalysisHistory.ResultSnapshot(
                        AiAnalysisStatus.FAILED,
                        exception.getFailureType()
                ))
                .build();

        saveHistorySafely(history);
    }

    private List<String> errorMessages(Exception exception) {
        String message = exception.getMessage() != null
                ? exception.getMessage()
                : exception.getClass().getSimpleName();

        return List.of(message);
    }

    private void savedPromptFailureHistory(
            AiRiskAnalysisRequestedEvent event,
            AiAnalysisException exception
    ){
        AiAnalysisHistory history = AiAnalysisHistory.builder()
                .analysisId(analysisId(event))
                .userId(strategy(event).userId())
                .strategyId(strategy(event).strategyId())
                .backtestId(backtest(event).backtestId())
                .requestSnapshot(Map.of(
                        "strategy", strategy(event),
                        "backtest", backtest(event)
                ))
                .validation(new AiAnalysisHistory.ValidationSnapshot(
                        false,
                        false,
                        errorMessages(exception)
                ))
                .result(new AiAnalysisHistory.ResultSnapshot(
                        AiAnalysisStatus.FAILED,
                        AiAnalysisFailureType.PROMPT_NOT_FOUND
                ))
                .build();

        saveHistorySafely(history);
    }

    private void saveInternalFailureHistory(
            AiRiskAnalysisRequestedEvent event,
            AiRiskAnalysisOutput output,
            Exception exception
    ) {
        AiAnalysisHistory.AiAnalysisHistoryBuilder builder = AiAnalysisHistory.builder()
                .analysisId(analysisId(event))
                .userId(strategy(event).userId())
                .strategyId(strategy(event).strategyId())
                .backtestId(backtest(event).backtestId())
                .requestSnapshot(Map.of(
                        "strategy", strategy(event),
                        "backtest", backtest(event)
                ))
                .validation(new AiAnalysisHistory.ValidationSnapshot(
                        false,
                        false,
                        errorMessages(exception)
                ))
                .result(new AiAnalysisHistory.ResultSnapshot(
                        AiAnalysisStatus.FAILED,
                        AiAnalysisFailureType.INTERNAL_ERROR
                ));

        if(output != null){
            builder
                    .prompt(new AiAnalysisHistory.PromptSnapshot(
                            output.promptKey(),
                            output.promptVersion(),
                            output.promptContent()
                    ))
                    .response(new AiAnalysisHistory.ResponseSnapshot(
                            output.rawResponse()
                    ))
                    .metadata(new AiAnalysisHistory.MetadataSnapshot(
                            output.model(),
                            output.latencyMs()
                    ));
        }

        saveHistorySafely(builder.build());
    }

    public void process(AiRiskAnalysisRequestedEvent event){

        UUID analysisId = analysisId(event);

        if (!aiRiskAnalysisQueryService.isPending(analysisId)) {
            log.info(
                    "이미 처리된 AI 위험 분석 이벤트를 건너뜁니다. eventId={}, analysisId={}",
                    event.eventId(),
                    analysisId
            );
            return;
        }

        AiRiskAnalysisOutput output = null;

        try{
            output = aiRiskAnalyzer.analyze(
                    strategy(event),
                    backtest(event)
            );

            AiRiskAnalysisResult result = output.result();

            responseValidator.validate(result);

            aiRiskAnalysisResultService.complete(
                    analysisId(event),
                    result.riskLevel(),
                    result.summary(),
                    result.riskFactors(),
                    result.reasoning(),
                    result.recommendations()
            );

            saveSuccessHistory(event, output);

        } catch (AiResponseValidationException e){

            aiRiskAnalysisResultService.fail(
                    analysisId(event),
                    AiAnalysisFailureType.VALIDATION_ERROR,
                    e.getMessage()
            );

            saveValidationFailureHistory(event, output, e);

        } catch (AiResponseParseException e){
            aiRiskAnalysisResultService.fail(
                    analysisId(event),
                    AiAnalysisFailureType.RESPONSE_PARSE_ERROR,
                    e.getMessage()
            );

            saveParseFailureHistory(event, e);

        } catch (AiAnalysisException e){
            aiRiskAnalysisResultService.fail(
                    analysisId(event),
                    e.getFailureType(),
                    e.getMessage()
            );

            if(e.getFailureType() == AiAnalysisFailureType.PROMPT_NOT_FOUND){
                savedPromptFailureHistory(event, e);
            } else {
                saveLlmFailureHistory(event, e);
            }

        } catch (Exception e){
            log.error(
                    "AI 위험 분석 처리 중 예상하지 못한 오류 발생. analysisId={}",
                    analysisId(event),
                    e
            );

            aiRiskAnalysisResultService.fail(
                    analysisId(event),
                    AiAnalysisFailureType.INTERNAL_ERROR,
                    e.getMessage()
            );

            saveInternalFailureHistory(event, output, e);
        }
    }
}
