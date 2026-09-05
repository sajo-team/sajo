package com.sajo.trading_service.ai_risk.controller.dto.response;

import com.sajo.trading_service.ai_risk.domain.AiAnalysisStatus;
import com.sajo.trading_service.ai_risk.domain.AiRiskAnalysis;
import com.sajo.trading_service.ai_risk.domain.RiskLevel;

import java.time.Instant;
import java.util.UUID;

public record AiRiskAnalysisHistoryItemResponse(
        UUID analysisId,
        UUID strategyId,
        UUID backtestId,
        AiAnalysisStatus status,
        RiskLevel riskLevel,
        String summary,
        Instant createdAt
) {

    public static AiRiskAnalysisHistoryItemResponse from(
            AiRiskAnalysis analysis
    ){
        return new AiRiskAnalysisHistoryItemResponse(
                analysis.getId(),
                analysis.getStrategyId(),
                analysis.getBacktestId(),
                analysis.getStatus(),
                analysis.getRiskLevel(),
                analysis.getSummary(),
                analysis.getCreatedAt()
        );
    }
}
