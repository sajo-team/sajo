package com.sajo.trading_service.ai_risk.service.analysis;

import com.sajo.trading_service.ai_risk.client.backtest.dto.BacktestInternalResponse;
import com.sajo.trading_service.ai_risk.client.strategy.dto.StrategyInternalResponse;
import com.sajo.trading_service.ai_risk.domain.AiAnalysisFailureType;
import com.sajo.trading_service.ai_risk.exception.AiAnalysisException;
import com.sajo.trading_service.ai_risk.service.analysis.dto.AiRiskAnalysisResult;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiRiskAnalyzer {

    private final  BeanOutputConverter<AiRiskAnalysisResult> outputConverter = new BeanOutputConverter<>(AiRiskAnalysisResult.class);

    private final ChatClient chatClient;

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

    public AiRiskAnalysisResult analyze(
            StrategyInternalResponse strategy,
            BacktestInternalResponse backtest
    ){

        //TODO: 관리자 프롬프트 버전 관리 구현 후 ACTIVE 프롬프트 조회 방식으로 변경
        String systemPrompt = """
                당신은 주식 자동매매 전략의 위험을 분석하는 AI입니다.
                
                제공된 전략 정보와 백테스트 결과를 기반으로 위험을 분석하세요.
                위험 등급은 LOW, MEDIUM, HIGH 중 하나로 판단하세요.
                
                반드시 제공된 데이터만 분석 근거로 사용하고,
                제공되지 않은 수치나 사실을 임의로 생성하지 마세요.
                
                반드시 다음 응답 형식을 준수하세요.
                
                %s
                """.formatted(outputConverter.getFormat());

        String analysisData = createAnalysisData(strategy, backtest);

        String rawResponse;

        try{
            rawResponse = chatClient.prompt()
                    .system(systemPrompt)
                    .user(analysisData)
                    .call()
                    .content();
        } catch (Exception e){
            throw new AiAnalysisException(
                    AiAnalysisFailureType.LLM_API_ERROR,
                    "LLM API 호출에 실패했습니다.",
                    e
            );
        }

        try{
            return outputConverter.convert(rawResponse);

        } catch (Exception e){
            throw new AiAnalysisException(
                    AiAnalysisFailureType.RESPONSE_PARSE_ERROR,
                    "AI 응답 변환에 실패했습니다.",
                    e
            );
        }
    }
}
