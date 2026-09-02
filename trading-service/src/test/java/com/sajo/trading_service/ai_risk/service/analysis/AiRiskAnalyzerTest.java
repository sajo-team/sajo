package com.sajo.trading_service.ai_risk.service.analysis;

import com.sajo.trading_service.ai_risk.client.backtest.dto.BacktestInternalResponse;
import com.sajo.trading_service.ai_risk.client.strategy.dto.StrategyInternalResponse;
import com.sajo.trading_service.ai_risk.domain.AiAnalysisFailureType;
import com.sajo.trading_service.ai_risk.domain.RiskFactorType;
import com.sajo.trading_service.ai_risk.domain.RiskLevel;
import com.sajo.trading_service.ai_risk.exception.AiAnalysisException;
import com.sajo.trading_service.ai_risk.exception.AiResponseParseException;
import com.sajo.trading_service.ai_risk.service.analysis.dto.AiRiskAnalysisOutput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("ai-risk")
@ExtendWith(MockitoExtension.class)
class AiRiskAnalyzerTest {

    private ChatClient chatClient;
    private AiRiskAnalyzer aiRiskAnalyzer;

    private StrategyInternalResponse strategy;
    private BacktestInternalResponse backtest;

    private StrategyInternalResponse createStrategy(
            UUID strategyId,
            UUID userId
    ) {
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

    private BacktestInternalResponse createBacktest(
            UUID strategyId,
            UUID userId
    ) {
        return new BacktestInternalResponse(
                UUID.randomUUID(),
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
        chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        aiRiskAnalyzer = new AiRiskAnalyzer(chatClient);

        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();

        strategy = createStrategy(strategyId, userId);
        backtest = createBacktest(strategyId, userId);
    }

    @Test
    @DisplayName("LLM이 정상적인 JSON을 반환하면 AI 위험 분석 결과를 반환한다")
    void analyze_success() {
        String rawResponse = """
                {
                  "riskLevel": "HIGH",
                  "summary": "최대 낙폭으로 인한 위험이 있습니다.",
                  "riskFactors": [
                    {
                      "type": "MAX_DRAWDOWN",
                      "description": "최대 낙폭이 높습니다."
                    }
                  ],
                  "reasoning": "백테스트의 최대 낙폭을 기준으로 판단했습니다.",
                  "recommendations": [
                    "손절 기준을 검토하세요."
                  ]
                }
                """;

        when(chatClient.prompt()
                .system(anyString())
                .user(anyString())
                .call()
                .content())
                .thenReturn(rawResponse);

        AiRiskAnalysisOutput output =
                aiRiskAnalyzer.analyze(strategy, backtest);

        assertThat(output).isNotNull();
        assertThat(output.rawResponse()).isEqualTo(rawResponse);
        assertThat(output.promptVersion()).isEqualTo("TEMP");
        assertThat(output.model()).isEqualTo("gpt-5-mini");
        assertThat(output.latencyMs()).isGreaterThanOrEqualTo(0L);

        assertThat(output.result().riskLevel())
                .isEqualTo(RiskLevel.HIGH);

        assertThat(output.result().summary())
                .isEqualTo("최대 낙폭으로 인한 위험이 있습니다.");

        assertThat(output.result().riskFactors())
                .hasSize(1);

        assertThat(output.result().riskFactors().get(0).type())
                .isEqualTo(RiskFactorType.MAX_DRAWDOWN);

        assertThat(output.result().reasoning())
                .isEqualTo("백테스트의 최대 낙폭을 기준으로 판단했습니다.");

        assertThat(output.result().recommendations())
                .containsExactly("손절 기준을 검토하세요.");
    }

    @Test
    @DisplayName("LLM API 호출 중 예외가 발생하면 LLM_API_ERROR가 발생한다")
    void analyze_llmApiFailure_throwsException() {
        when(chatClient.prompt()
                .system(anyString())
                .user(anyString())
                .call()
                .content())
                .thenThrow(new RuntimeException("OpenAI API error"));

        assertThatThrownBy(() ->
                aiRiskAnalyzer.analyze(strategy, backtest)
        )
                .isInstanceOf(AiAnalysisException.class)
                .satisfies(exception -> {
                    AiAnalysisException aiException =
                            (AiAnalysisException) exception;

                    assertThat(aiException.getFailureType())
                            .isEqualTo(AiAnalysisFailureType.LLM_API_ERROR);

                    assertThat(aiException.getMessage())
                            .isEqualTo("LLM API 호출에 실패했습니다.");

                    assertThat(aiException.getCause())
                            .isInstanceOf(RuntimeException.class);
                });
    }

    @Test
    @DisplayName("LLM 응답을 파싱할 수 없으면 원본 응답을 포함한 AiResponseParseException이 발생한다")
    void analyze_parseFailure_throwsExceptionWithRawResponse() {
        String rawResponse = "이것은 JSON 응답이 아닙니다.";

        when(chatClient.prompt()
                .system(anyString())
                .user(anyString())
                .call()
                .content())
                .thenReturn(rawResponse);

        assertThatThrownBy(() ->
                aiRiskAnalyzer.analyze(strategy, backtest)
        )
                .isInstanceOf(AiResponseParseException.class)
                .satisfies(exception -> {
                    AiResponseParseException parseException =
                            (AiResponseParseException) exception;

                    assertThat(parseException.getFailureType())
                            .isEqualTo(
                                    AiAnalysisFailureType.RESPONSE_PARSE_ERROR
                            );

                    assertThat(parseException.getMessage())
                            .isEqualTo("AI 응답 변환에 실패했습니다.");

                    assertThat(parseException.getRawResponse())
                            .isEqualTo(rawResponse);
                });
    }
}