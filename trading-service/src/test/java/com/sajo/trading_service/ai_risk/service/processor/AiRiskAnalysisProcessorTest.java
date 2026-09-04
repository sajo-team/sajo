package com.sajo.trading_service.ai_risk.service.processor;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.ai_risk.client.backtest.dto.BacktestInternalResponse;
import com.sajo.trading_service.ai_risk.client.strategy.dto.StrategyInternalResponse;
import com.sajo.trading_service.ai_risk.document.AiAnalysisHistory;
import com.sajo.trading_service.ai_risk.domain.*;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("ai-risk")
@ExtendWith(MockitoExtension.class)
class AiRiskAnalysisProcessorTest {

    @Mock
    private AiRiskAnalyzer aiRiskAnalyzer;

    @Mock
    private AiRiskAnalysisResultService resultService;

    @Mock
    private AiRiskResponseValidator responseValidator;

    @Mock
    private AiAnalysisHistoryCommandRepository historyRepository;

    @InjectMocks
    private AiRiskAnalysisProcessor processor;

    private UUID analysisId;
    private UUID userId;
    private UUID strategyId;
    private UUID backtestId;

    private StrategyInternalResponse strategy;
    private BacktestInternalResponse backtest;
    private AiRiskAnalysisRequestedEvent event;

    @BeforeEach
    void setUp() {
        analysisId = UUID.randomUUID();
        userId = UUID.randomUUID();
        strategyId = UUID.randomUUID();
        backtestId = UUID.randomUUID();

        strategy = createStrategy();
        backtest = createBacktest();

        event = new AiRiskAnalysisRequestedEvent(
                analysisId,
                strategy,
                backtest
        );
    }

    @Test
    @DisplayName("AI 분석과 검증에 성공하면 분석을 완료하고 성공 이력을 저장한다")
    void process_success() {
        AiRiskAnalysisResult result = createResult();
        AiRiskAnalysisOutput output = createOutput(result);

        when(aiRiskAnalyzer.analyze(strategy, backtest))
                .thenReturn(output);

        processor.process(event);

        verify(responseValidator).validate(result);

        verify(resultService).complete(
                analysisId,
                result.riskLevel(),
                result.summary(),
                result.riskFactors(),
                result.reasoning(),
                result.recommendations()
        );

        verify(resultService, never())
                .fail(any(), any(), any());

        ArgumentCaptor<AiAnalysisHistory> captor =
                ArgumentCaptor.forClass(AiAnalysisHistory.class);

        verify(historyRepository).save(captor.capture());

        AiAnalysisHistory history = captor.getValue();

        assertThat(history.getAnalysisId()).isEqualTo(analysisId);
        assertThat(history.getUserId()).isEqualTo(userId);
        assertThat(history.getStrategyId()).isEqualTo(strategyId);
        assertThat(history.getBacktestId()).isEqualTo(backtestId);

        assertThat(history.getPrompt().version())
                .isEqualTo("v3");

        assertThat(history.getPrompt().content())
                .isEqualTo("테스트 시스템 프롬프트");

        assertThat(history.getResponse().rawResponse())
                .isEqualTo("{\"riskLevel\":\"HIGH\"}");

        assertThat(history.getValidation().structureValid())
                .isTrue();

        assertThat(history.getValidation().contentValid())
                .isTrue();

        assertThat(history.getValidation().errors())
                .isEmpty();

        assertThat(history.getMetadata().model())
                .isEqualTo("gpt-5-mini");

        assertThat(history.getMetadata().latencyMs())
                .isEqualTo(100L);
    }

    @Test
    @DisplayName("AI 응답 구조 검증에 실패하면 VALIDATION_ERROR로 실패 처리하고 이력을 저장한다")
    void process_structureValidationFailure() {
        AiRiskAnalysisResult result = createResult();
        AiRiskAnalysisOutput output = createOutput(result);

        when(aiRiskAnalyzer.analyze(strategy, backtest))
                .thenReturn(output);

        doThrow(new AiResponseValidationException(
                AiValidationType.STRUCTURE,
                "AI 응답 구조가 올바르지 않습니다."
        )).when(responseValidator).validate(result);

        processor.process(event);

        verify(resultService).fail(
                analysisId,
                AiAnalysisFailureType.VALIDATION_ERROR,
                "AI 응답 구조가 올바르지 않습니다."
        );

        verify(resultService, never())
                .complete(any(), any(), any(), any(), any(), any());

        ArgumentCaptor<AiAnalysisHistory> captor =
                ArgumentCaptor.forClass(AiAnalysisHistory.class);

        verify(historyRepository).save(captor.capture());

        AiAnalysisHistory history = captor.getValue();

        assertThat(history.getValidation().structureValid())
                .isFalse();

        assertThat(history.getValidation().contentValid())
                .isFalse();

        assertThat(history.getValidation().errors())
                .containsExactly("AI 응답 구조가 올바르지 않습니다.");

        assertThat(history.getResponse().rawResponse())
                .isEqualTo("{\"riskLevel\":\"HIGH\"}");
    }

    @Test
    @DisplayName("AI 응답 내용 검증에 실패하면 구조 검증 성공과 내용 검증 실패를 이력에 저장한다")
    void process_contentValidationFailure() {
        AiRiskAnalysisResult result = createResult();
        AiRiskAnalysisOutput output = createOutput(result);

        when(aiRiskAnalyzer.analyze(strategy, backtest))
                .thenReturn(output);

        doThrow(new AiResponseValidationException(
                AiValidationType.CONTENT,
                "AI 응답 내용이 올바르지 않습니다."
        )).when(responseValidator).validate(result);

        processor.process(event);

        verify(resultService).fail(
                analysisId,
                AiAnalysisFailureType.VALIDATION_ERROR,
                "AI 응답 내용이 올바르지 않습니다."
        );

        ArgumentCaptor<AiAnalysisHistory> captor =
                ArgumentCaptor.forClass(AiAnalysisHistory.class);

        verify(historyRepository).save(captor.capture());

        AiAnalysisHistory history = captor.getValue();

        assertThat(history.getValidation().structureValid())
                .isTrue();

        assertThat(history.getValidation().contentValid())
                .isFalse();
    }

    @Test
    @DisplayName("AI 응답 파싱에 실패하면 사용한 프롬프트와 원본 응답을 이력에 저장한다")
    void process_parseFailure() {
        String rawResponse = "invalid json";

        AiResponseParseException exception =
                new AiResponseParseException(
                        "AI 응답 변환에 실패했습니다.",
                        rawResponse,
                        "v3",
                        "테스트 시스템 프롬프트",
                        "gpt-5-mini",
                        100L,
                        new RuntimeException("parse error")
                );

        when(aiRiskAnalyzer.analyze(strategy, backtest))
                .thenThrow(exception);

        processor.process(event);

        verify(resultService).fail(
                analysisId,
                AiAnalysisFailureType.RESPONSE_PARSE_ERROR,
                "AI 응답 변환에 실패했습니다."
        );

        verifyNoInteractions(responseValidator);

        ArgumentCaptor<AiAnalysisHistory> captor =
                ArgumentCaptor.forClass(AiAnalysisHistory.class);

        verify(historyRepository).save(captor.capture());

        AiAnalysisHistory history = captor.getValue();

        assertThat(history.getPrompt()).isNotNull();

        assertThat(history.getPrompt().version()).isEqualTo("v3");

        assertThat(history.getPrompt().content()).isEqualTo("테스트 시스템 프롬프트");

        assertThat(history.getResponse().rawResponse())
                .isEqualTo(rawResponse);

        assertThat(history.getValidation().structureValid())
                .isFalse();

        assertThat(history.getValidation().contentValid())
                .isFalse();

        assertThat(history.getValidation().errors())
                .containsExactly("AI 응답 변환에 실패했습니다.");

        assertThat(history.getMetadata().model())
                .isEqualTo("gpt-5-mini");

        assertThat(history.getMetadata().latencyMs())
                .isEqualTo(100L);
    }

    @Test
    @DisplayName("LLM 호출에 실패하면 해당 실패 유형으로 처리하고 실패 이력을 저장한다")
    void process_llmFailure() {
        AiAnalysisException exception =
                new AiAnalysisException(
                        AiAnalysisFailureType.LLM_API_ERROR,
                        "LLM API 호출에 실패했습니다.",
                        "v3",
                        "테스트 시스템 프롬프트",
                        "gpt-5-mini",
                        150L,
                        new RuntimeException("OpenAI error")
                );

        when(aiRiskAnalyzer.analyze(strategy, backtest))
                .thenThrow(exception);

        processor.process(event);

        verify(resultService).fail(
                analysisId,
                AiAnalysisFailureType.LLM_API_ERROR,
                "LLM API 호출에 실패했습니다."
        );

        verifyNoInteractions(responseValidator);

        ArgumentCaptor<AiAnalysisHistory> captor =
                ArgumentCaptor.forClass(AiAnalysisHistory.class);

        verify(historyRepository).save(captor.capture());

        AiAnalysisHistory history = captor.getValue();

        assertThat(history.getAnalysisId())
                .isEqualTo(analysisId);

        assertThat(history.getPrompt()).isNotNull();

        assertThat(history.getPrompt().version())
                .isEqualTo("v3");

        assertThat(history.getPrompt().content())
                .isEqualTo("테스트 시스템 프롬프트");

        assertThat(history.getMetadata()).isNotNull();

        assertThat(history.getMetadata().model())
                .isEqualTo("gpt-5-mini");

        assertThat(history.getMetadata().latencyMs())
                .isEqualTo(150L);

        assertThat(history.getValidation().structureValid())
                .isFalse();

        assertThat(history.getValidation().contentValid())
                .isFalse();

        assertThat(history.getValidation().errors())
                .containsExactly("LLM API 호출에 실패했습니다.");
    }

    @Test
    @DisplayName("MongoDB 감사 이력 저장에 실패해도 완료된 AI 분석 처리는 실패하지 않는다")
    void process_historySaveFailure_doesNotAffectAnalysis() {
        AiRiskAnalysisResult result = createResult();
        AiRiskAnalysisOutput output = createOutput(result);

        when(aiRiskAnalyzer.analyze(strategy, backtest))
                .thenReturn(output);

        when(historyRepository.save(any(AiAnalysisHistory.class)))
                .thenThrow(new RuntimeException("MongoDB error"));

        assertThatCode(() -> processor.process(event))
                .doesNotThrowAnyException();

        verify(resultService).complete(
                analysisId,
                result.riskLevel(),
                result.summary(),
                result.riskFactors(),
                result.reasoning(),
                result.recommendations()
        );

        verify(resultService, never())
                .fail(any(), any(), any());
    }

    private AiRiskAnalysisResult createResult() {
        return new AiRiskAnalysisResult(
                RiskLevel.HIGH,
                "위험도가 높은 전략입니다.",
                List.of(
                        new RiskFactor(
                                RiskFactorType.MAX_DRAWDOWN,
                                "최대 낙폭이 높습니다."
                        )
                ),
                "백테스트 결과를 기준으로 위험도가 높다고 판단했습니다.",
                List.of("손절 기준을 검토하세요.")
        );
    }

    private AiRiskAnalysisOutput createOutput(
            AiRiskAnalysisResult result
    ) {
        return new AiRiskAnalysisOutput(
                result,
                "{\"riskLevel\":\"HIGH\"}",
                "테스트 시스템 프롬프트",
                "v3",
                "gpt-5-mini",
                100L
        );
    }

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

    @Test
    @DisplayName("예상하지 못한 예외가 발생하면 INTERNAL_ERROR로 실패 처리한다")
    void process_unexpectedException_failsWithInternalError() {
        AiRiskAnalysisResult result = createResult();
        AiRiskAnalysisOutput output = createOutput(result);

        when(aiRiskAnalyzer.analyze(strategy, backtest))
                .thenReturn(output);

        doThrow(new RuntimeException("unexpected error"))
                .when(responseValidator)
                .validate(result);

        processor.process(event);

        verify(resultService).fail(
                analysisId,
                AiAnalysisFailureType.INTERNAL_ERROR,
                "unexpected error"
        );

        verify(resultService, never())
                .complete(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("ACTIVE 프롬프트가 없으면 PROMPT_NOT_FOUND로 실패 처리하고 감사 이력을 저장한다")
    void process_activePromptNotFound_savesFailureHistory() {
        // given
        BusinessException exception =
                new BusinessException(AiRiskErrorCode.AI_ACTIVE_PROMPT_NOT_FOUND);

        when(aiRiskAnalyzer.analyze(any(), any()))
                .thenThrow(exception);

        // when
        processor.process(event);

        // then
        verify(resultService).fail(
                eq(event.analysisId()),
                eq(AiAnalysisFailureType.PROMPT_NOT_FOUND),
                eq(exception.getMessage())
        );

        ArgumentCaptor<AiAnalysisHistory> captor =
                ArgumentCaptor.forClass(AiAnalysisHistory.class);

        verify(historyRepository).save(captor.capture());

        AiAnalysisHistory history = captor.getValue();

        assertThat(history.getAnalysisId())
                .isEqualTo(event.analysisId());

        assertThat(history.getPrompt())
                .isNull();

        assertThat(history.getResponse())
                .isNull();

        assertThat(history.getMetadata())
                .isNull();

        assertThat(history.getValidation().structureValid())
                .isFalse();

        assertThat(history.getValidation().contentValid())
                .isFalse();

        assertThat(history.getValidation().errors())
                .containsExactly(exception.getMessage());
    }
}