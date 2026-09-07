package com.sajo.trading_service.ai_risk.service.analysis;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.ai_risk.client.backtest.dto.BacktestInternalResponse;
import com.sajo.trading_service.ai_risk.client.strategy.dto.StrategyInternalResponse;
import com.sajo.trading_service.ai_risk.domain.AiAnalysisFailureType;
import com.sajo.trading_service.ai_risk.domain.AiPromptKey;
import com.sajo.trading_service.ai_risk.domain.AiPromptVersion;
import com.sajo.trading_service.ai_risk.exception.AiAnalysisException;
import com.sajo.trading_service.ai_risk.exception.AiResponseParseException;
import com.sajo.trading_service.ai_risk.exception.AiRiskErrorCode;
import com.sajo.trading_service.ai_risk.service.analysis.dto.AiRiskAnalysisOutput;
import com.sajo.trading_service.ai_risk.service.analysis.dto.AiRiskAnalysisResult;
import com.sajo.trading_service.ai_risk.service.query.AiPromptVersionQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiRiskAnalyzer {

    private final BeanOutputConverter<AiRiskAnalysisResult> outputConverter = new BeanOutputConverter<>(AiRiskAnalysisResult.class);
    private final ChatClient chatClient;
    private final AiPromptVersionQueryService promptVersionQueryService;

    private static final String MODEL = "gpt-5-mini";

    private String createAnalysisData(
            StrategyInternalResponse strategy,
            BacktestInternalResponse backtest
    ) {
        return """
                [전략]
                종목 코드: %s
                전략명: %s
                매수 기준 가격: %s
                매도 기준 가격: %s
                손절률: %s
                목표 수익률: %s
                할당 금액: %s
                1회 주문 금액: %s
                PER 조건: %s
                PBR 조건: %s
                ROE 조건: %s

                [백테스트]
                기간: %s ~ %s
                총 수익률: %s
                MDD: %s
                승률: %s
                거래 횟수: %s
                최대 연속 손실: %s
                """.formatted(
                strategy.stockCode(),
                strategy.strategyName(),
                strategy.buyConditionPrice(),
                strategy.sellConditionPrice(),
                strategy.stopLossRate(),
                strategy.targetReturnRate(),
                strategy.allocatedAmount(),
                strategy.orderAmount(),
                strategy.perCondition(),
                strategy.pbrCondition(),
                strategy.roeCondition(),
                backtest.startDate(),
                backtest.endDate(),
                backtest.totalReturnRate(),
                backtest.mdd(),
                backtest.winRate(),
                backtest.tradeCount(),
                backtest.maxConsecutiveLosses()
        );
    }

    private String createSystemPrompt(String promptContent){
        return """
                %s
                
                [응답 규칙]
                위험 등급은 반드시 LOW, MEDIUM, HIGH 중 하나로 판단하세요.
                
                응답은 반드시 아래에 정의된 JSON 형식으로만 작성하세요.
                JSON 이외의 설명, 마크다운 코드 블록, 추가 텍스트를 포함하지 마세요.
                모든 필드는 반드시 포함하세요.
                
                %s
                """.formatted(
                        promptContent,
                outputConverter.getFormat()
        );
    }

    public AiRiskAnalysisOutput analyze(
            StrategyInternalResponse strategy,
            BacktestInternalResponse backtest
    ){
        AiPromptVersion promptVersion;

        try{
            promptVersion = promptVersionQueryService.getActivePrompt(
                    AiPromptKey.RISK_ANALYSIS
            );
        } catch (BusinessException e){
            if(e.getErrorCode() == AiRiskErrorCode.AI_ACTIVE_PROMPT_NOT_FOUND) {
                throw new AiAnalysisException(
                        AiAnalysisFailureType.PROMPT_NOT_FOUND,
                        e.getMessage(),
                        null,
                        null,
                        MODEL,
                        0L,
                        e
                );
            }

            throw e;
        }

        String promptContent = promptVersion.getPromptContent();
        String version = promptVersion.getVersion();

        String systemPrompt = createSystemPrompt(promptContent);

        String analysisData = createAnalysisData(strategy, backtest);

        long startTime = System.currentTimeMillis();
        String rawResponse;

        try{
            rawResponse = chatClient.prompt()
                    .system(systemPrompt)
                    .user(analysisData)
                    .call()
                    .content();
        } catch (Exception e){
            long latencyMs = System.currentTimeMillis() - startTime;

            throw new AiAnalysisException(
                    AiAnalysisFailureType.LLM_API_ERROR,
                    "LLM API 호출에 실패했습니다.",
                    promptVersion.getVersion(),
                    systemPrompt,
                    MODEL,
                    latencyMs,
                    e
            );
        }

        long latencyMs = System.currentTimeMillis() - startTime;

        try{
            AiRiskAnalysisResult result = outputConverter.convert(rawResponse);

            return new AiRiskAnalysisOutput(
                    result,
                    rawResponse,
                    systemPrompt,
                    version,
                    MODEL,
                    latencyMs
            );

        } catch (Exception e){
            throw new AiResponseParseException(
                    "AI 응답 변환에 실패했습니다.",
                    rawResponse,
                    promptVersion.getVersion(),
                    systemPrompt,
                    MODEL,
                    latencyMs,
                    e
            );
        }
    }
}
