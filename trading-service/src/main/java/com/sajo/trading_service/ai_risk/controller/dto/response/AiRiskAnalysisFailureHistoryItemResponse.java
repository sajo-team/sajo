package com.sajo.trading_service.ai_risk.controller.dto.response;

import com.sajo.trading_service.ai_risk.domain.AiAnalysisFailureType;
import com.sajo.trading_service.ai_risk.domain.AiRiskAnalysis;

import java.time.Instant;
import java.util.UUID;

public record AiRiskAnalysisFailureHistoryItemResponse(
        UUID analysisId,
        UUID userId,
        UUID strategyId,
        UUID backtestId,
        AiAnalysisFailureType failureType,
        String failureMessage,
        Instant createdAt
) {

    public static AiRiskAnalysisFailureHistoryItemResponse from(
            AiRiskAnalysis analysis
    ){
        return new AiRiskAnalysisFailureHistoryItemResponse(
                analysis.getId(),
                analysis.getUserId(),
                analysis.getStrategyId(),
                analysis.getBacktestId(),
                analysis.getFailureType(),
                analysis.getFailureMessage(),
                analysis.getCreatedAt()
        );
    }
}
