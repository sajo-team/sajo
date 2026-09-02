package com.sajo.trading_service.ai_risk.service.analysis;

import com.sajo.trading_service.ai_risk.exception.AiResponseValidationException;
import com.sajo.trading_service.ai_risk.service.analysis.dto.AiRiskAnalysisResult;
import org.springframework.stereotype.Component;

@Component
public class AiRiskResponseValidator {

    public void validate(AiRiskAnalysisResult result){
        validateStructure(result);
        validateContent(result);
    }

    private void validateStructure(AiRiskAnalysisResult result){
        if(result == null){
            throw new AiResponseValidationException("AI 분석 응답이 없습니다.");
        }

        if (result.riskLevel() == null) {
            throw new AiResponseValidationException("위험 등급이 없습니다.");
        }

        if (result.summary() == null || result.summary().isBlank()) {
            throw new AiResponseValidationException("분석 요약이 없습니다.");
        }

        if (result.riskFactors() == null) {
            throw new AiResponseValidationException("위험 요인 목록이 없습니다.");
        }

        if (result.reasoning() == null || result.reasoning().isBlank()) {
            throw new AiResponseValidationException("분석 근거가 없습니다.");
        }

        if (result.recommendations() == null) {
            throw new AiResponseValidationException("개선 권고 목록이 없습니다.");
        }
    }

    private void validateContent(AiRiskAnalysisResult result) {
        if (result.riskFactors().stream().anyMatch(factor ->
                factor == null
                        || factor.type() == null
                        || factor.description() == null
                        || factor.description().isBlank())) {
            throw new AiResponseValidationException("유효하지 않은 위험 요인이 포함되어 있습니다.");
        }

        if (result.recommendations().stream().anyMatch(recommendation ->
                recommendation == null || recommendation.isBlank())) {
            throw new AiResponseValidationException("유효하지 않은 개선 권고가 포함되어 있습니다.");
        }
    }
}
