package com.sajo.trading_service.ai_risk.service.analysis;

import com.sajo.trading_service.ai_risk.domain.AiValidationType;
import com.sajo.trading_service.ai_risk.domain.RiskFactor;
import com.sajo.trading_service.ai_risk.domain.RiskFactorType;
import com.sajo.trading_service.ai_risk.domain.RiskLevel;
import com.sajo.trading_service.ai_risk.exception.AiResponseValidationException;
import com.sajo.trading_service.ai_risk.service.analysis.dto.AiRiskAnalysisResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
@Tag("ai-risk")
class AiRiskResponseValidatorTest {

    private AiRiskResponseValidator validator;

    private AiRiskAnalysisResult createValidResult(){
        return new AiRiskAnalysisResult(
                RiskLevel.LOW,
                "백테스트 결과 일부 위험 요인이 확인되었습니다.",
                createValidRiskFactors(),
                "최대 낙폭과 연속 손실을 기준으로 위험도를 판단했습니다.",
                List.of("손절 기준을 검토하세요.")
        );
    }

    private List<RiskFactor> createValidRiskFactors(){
        return List.of(
                new RiskFactor(
                        RiskFactorType.MAX_DRAWDOWN,
                        "최대 낙폭이 높습니다."
                )
        );
    }

    private void assertValidationException(
            AiRiskAnalysisResult result,
            AiValidationType expectedType,
            String expectedMessage
    ){
        assertThatThrownBy(() -> validator.validate(result))
                .isInstanceOf(AiResponseValidationException.class)
                .satisfies(exception -> {
                    AiResponseValidationException validationException = (AiResponseValidationException) exception;

                    assertThat(validationException.getValidationType()).isEqualTo(expectedType);

                    assertThat(validationException.getMessage()).isEqualTo(expectedMessage);
                });
    }

    @BeforeEach
    void setUp(){
        validator = new AiRiskResponseValidator();
    }

    @Test
    @DisplayName("정상적인 AI 분석 응답이면 검증에 성공한다")
    void validate_success() {
        AiRiskAnalysisResult result = createValidResult();

        assertThatCode(() -> validator.validate(result))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("AI 분석 응답이 null이면 STRUCTURE 검증에 실패한다")
    void validate_nullResult_throwsException() {
        assertValidationException(
                null,
                AiValidationType.STRUCTURE,
                "AI 분석 응답이 없습니다."
        );
    }

    @Test
    @DisplayName("위험 등급이 없으면 STRUCTURE 검증에 실패한다")
    void validate_nullRiskLevel_throwsException() {
        AiRiskAnalysisResult result = new AiRiskAnalysisResult(
                null,
                "분석 요약",
                createValidRiskFactors(),
                "분석 근거",
                List.of("손절 기준을 검토하세요.")
        );

        assertValidationException(
                result,
                AiValidationType.STRUCTURE,
                "위험 등급이 없습니다."
        );
    }

    @Test
    @DisplayName("분석 요약이 비어있으면 STRUCTURE 검증에 실패한다")
    void validate_blankSummary_throwsException(){
        AiRiskAnalysisResult result = new AiRiskAnalysisResult(
                RiskLevel.LOW,
                " ",
                createValidRiskFactors(),
                "분석 근거",
                List.of("손절 기준을 검토하세요.")
        );

        assertValidationException(
                result,
                AiValidationType.STRUCTURE,
                "분석 요약이 없습니다."
        );
    }

    @Test
    @DisplayName("위험 요인 목록이 null이면 STRUCTURE 검증에 실패한다")
    void validate_nullRiskFactors_throwsException() {
        AiRiskAnalysisResult result = new AiRiskAnalysisResult(
                RiskLevel.LOW,
                "분석 요약",
                null,
                "분석 근거",
                List.of("손절 기준을 검토하세요.")
        );

        assertValidationException(
                result,
                AiValidationType.STRUCTURE,
                "위험 요인 목록이 없습니다."
        );
    }

    @Test
    @DisplayName("분석 근거가 비어있으면 STRUCTURE 검증에 실패한다")
    void validate_blankReasoning_throwsException() {
        AiRiskAnalysisResult result = new AiRiskAnalysisResult(
                RiskLevel.LOW,
                "분석 요약",
                createValidRiskFactors(),
                " ",
                List.of("손절 기준을 검토하세요.")
        );

        assertValidationException(
                result,
                AiValidationType.STRUCTURE,
                "분석 근거가 없습니다."
        );
    }

    @Test
    @DisplayName("개선 권고 목록이 null이면 STRUCTURE 검증에 실패한다")
    void validate_nullRecommendations_throwsException() {
        AiRiskAnalysisResult result = new AiRiskAnalysisResult(
                RiskLevel.LOW,
                "분석 요약",
                createValidRiskFactors(),
                "분석 근거",
                null
        );

        assertValidationException(
                result,
                AiValidationType.STRUCTURE,
                "개선 권고 목록이 없습니다."
        );
    }

    @Test
    @DisplayName("위험 요인의 타입이 없으면 CONTENT 검증에 실패한다")
    void validate_nullRiskFactorType_throwsException() {
        AiRiskAnalysisResult result = new AiRiskAnalysisResult(
                RiskLevel.LOW,
                "분석 요약",
                List.of(
                        new RiskFactor(
                                null,
                                "최대 낙폭이 높습니다."
                        )
                ),
                "분석 근거",
                List.of("손절 기준을 검토하세요.")
        );

        assertValidationException(
                result,
                AiValidationType.CONTENT,
                "유효하지 않은 위험 요인이 포함되어 있습니다."
        );
    }

    @Test
    @DisplayName("위험 요인의 설명이 비어있으면 CONTENT 검증에 실패한다")
    void validate_blankRiskFactorDescription_throwsException() {
        AiRiskAnalysisResult result = new AiRiskAnalysisResult(
                RiskLevel.LOW,
                "분석 요약",
                List.of(
                        new RiskFactor(
                                RiskFactorType.MAX_DRAWDOWN,
                                " "
                        )
                ),
                "분석 근거",
                List.of("손절 기준을 검토하세요.")
        );

        assertValidationException(
                result,
                AiValidationType.CONTENT,
                "유효하지 않은 위험 요인이 포함되어 있습니다."
        );
    }

    @Test
    @DisplayName("개선 권고가 비어있으면 CONTENT 검증에 실패한다")
    void validate_blankRecommendation_throwsException() {
        AiRiskAnalysisResult result = new AiRiskAnalysisResult(
                RiskLevel.LOW,
                "분석 요약",
                createValidRiskFactors(),
                "분석 근거",
                List.of(" ")
        );

        assertValidationException(
                result,
                AiValidationType.CONTENT,
                "유효하지 않은 개선 권고가 포함되어 있습니다."
        );
    }

}