package com.sajo.trading_service.ai_risk.controller.dto.response;

import com.sajo.trading_service.ai_risk.domain.AiAnalysisStatus;
import com.sajo.trading_service.ai_risk.domain.AiRiskAnalysis;
import com.sajo.trading_service.ai_risk.domain.RiskFactor;
import com.sajo.trading_service.ai_risk.domain.RiskLevel;

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
        List<String> recommendations
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
                analysis.getRecommendations()
        );
    }
}
