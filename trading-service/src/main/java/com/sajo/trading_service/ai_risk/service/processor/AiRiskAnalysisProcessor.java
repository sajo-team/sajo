package com.sajo.trading_service.ai_risk.service.processor;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.ai_risk.client.backtest.dto.BacktestInternalResponse;
import com.sajo.trading_service.ai_risk.client.strategy.dto.StrategyInternalResponse;
import com.sajo.trading_service.ai_risk.document.AiAnalysisHistory;
import com.sajo.trading_service.ai_risk.domain.AiAnalysisFailureType;
import com.sajo.trading_service.ai_risk.domain.AiValidationType;
import com.sajo.trading_service.ai_risk.event.AiRiskAnalysisRequestedEvent;
import com.sajo.trading_service.ai_risk.exception.AiAnalysisException;
import com.sajo.trading_service.ai_risk.exception.AiResponseParseException;
import com.sajo.trading_service.ai_risk.exception.AiResponseValidationException;
import com.sajo.trading_service.ai_risk.exception.AiRiskErrorCode;
import com.sajo.trading_service.ai_risk.repository.command.AiAnalysisHistoryCommandRepository;
import com.sajo.trading_service.ai_risk.service.analysis.AiRiskAnalyzer;
import com.sajo.trading_service.ai_risk.service.analysis.AiRiskResponseValidator;
import com.sajo.trading_service.ai_risk.service.analysis.dto.AiRiskAnalysisOutput;
import com.sajo.trading_service.ai_risk.service.analysis.dto.AiRiskAnalysisResult;
import com.sajo.trading_service.ai_risk.service.command.AiRiskAnalysisResultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
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
                .prompt(new AiAnalysisHistory.PromptSnapshot(
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
                        false,List.of(exception.getMessage())
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
                .analysisId(event.analysisId())
                .userId(event.strategy().userId())
                .strategyId(event.strategy().strategyId())
                .backtestId(event.backtest().backtestId())
                .requestSnapshot(Map.of(
                        "strategy", event.strategy(),
                        "backtest", event.backtest()
                ))
                .prompt(new AiAnalysisHistory.PromptSnapshot(
                        exception.getPromptVersion(),
                        exception.getPromptContent()
                ))
                .validation(new AiAnalysisHistory.ValidationSnapshot(
                        false,
                        false,
                        List.of(exception.getMessage())
                ))
                .metadata(new AiAnalysisHistory.MetadataSnapshot(
                        exception.getModel(),
                        exception.getLatencyMs()
                ))
                .build();

        saveHistorySafely(history);
    }

    private void savedPromptFailureHistory(
            AiRiskAnalysisRequestedEvent event,
            BusinessException exception
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

        historyCommandRepository.save(history);
    }

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
        } catch (BusinessException e){
          if(e.getErrorCode() == AiRiskErrorCode.AI_ACTIVE_PROMPT_NOT_FOUND){
              aiRiskAnalysisResultService.fail(
                      event.analysisId(),
                      AiAnalysisFailureType.PROMPT_NOT_FOUND,
                      e.getMessage()
              );

              savedPromptFailureHistory(event, e);

              log.warn(
                      "AI 위험 분석 실패 - 활성 프롬프트 없음. analysisId={}",
                      event.analysisId()
              );

              return;
          }

          throw e;
        } catch (Exception e){
            log.error(
                    "AI 위험 분석 처리 중 예상하지 못한 오류 발생. analysisId={}",
                    event.analysisId(),
                    e
            );

            aiRiskAnalysisResultService.fail(
                    event.analysisId(),
                    AiAnalysisFailureType.INTERNAL_ERROR,
                    e.getMessage()
            );
        }
    }
}
