package com.sajo.trading_service.ai_risk.controller.dto.response;

import com.sajo.trading_service.ai_risk.domain.*;

import java.util.List;
import java.util.UUID;

public record AiRiskAnalysisDetailResponse(
        UUID analysisId,
        UUID strategyId,
        UUID backtestId,
        AiAnalysisStatus status,
        RiskLevel riskLevel,
        String summary,
        List<RiskFactor> riskFactors,
        String reasoning,
        List<String> recommendations,
        AiAnalysisFailureType failureType,
        String message
) {

    public static AiRiskAnalysisDetailResponse from(AiRiskAnalysis analysis){
        return new AiRiskAnalysisDetailResponse(
                analysis.getId(),
                analysis.getStrategyId(),
                analysis.getBacktestId(),
                analysis.getStatus(),
                analysis.getRiskLevel(),
                analysis.getSummary(),
                analysis.getRiskFactors(),
                analysis.getReasoning(),
                analysis.getRecommendations(),
                analysis.getFailureType(),
                getMessage(analysis)
        );
    }

    private static String getMessage(AiRiskAnalysis analysis){
        return switch (analysis.getStatus()){
            case PENDING ->
                "AI 분석이 진행 중입니다.";
            case COMPLETED ->
                "AI 분석이 완료되었습니다.";
            case FAILED ->
                getFailMessage(analysis.getFailureType());
        };
    }

    private static String getFailMessage(AiAnalysisFailureType failureType){
        if (failureType == null) {
            return "AI 분석 처리 중 오류가 발생했습니다.";
        }

        return switch (failureType) {
            case LLM_API_ERROR ->
                    "AI 분석 요청 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.";
            case RESPONSE_PARSE_ERROR ->
                    "AI 분석 결과를 처리하는 중 오류가 발생했습니다.";
            case VALIDATION_ERROR ->
                    "AI 분석 결과를 검증하는 중 오류가 발생했습니다.";
            case INTERNAL_ERROR ->
                    "AI 분석 처리 중 오류가 발생했습니다.";
        };
    }
}
