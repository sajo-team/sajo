package com.sajo.trading_service.ai_risk.controller.dto.response;

import com.sajo.trading_service.ai_risk.domain.AiRiskAnalysis;
import com.sajo.trading_service.ai_risk.domain.AiAnalysisStatus;

import java.util.UUID;

public record AiRiskAnalysisCreateResponse(
        UUID analysisId,
        AiAnalysisStatus status
) {
    public static AiRiskAnalysisCreateResponse from(AiRiskAnalysis analysis){
        return new AiRiskAnalysisCreateResponse(
                analysis.getId(),
                analysis.getStatus()
        );
    }
}
