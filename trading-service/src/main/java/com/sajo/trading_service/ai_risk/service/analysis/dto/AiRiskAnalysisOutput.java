package com.sajo.trading_service.ai_risk.service.analysis.dto;

import com.sajo.trading_service.ai_risk.domain.AiPromptKey;

public record AiRiskAnalysisOutput(
        AiRiskAnalysisResult result,
        String rawResponse,
        AiPromptKey promptKey,
        String promptContent,
        String promptVersion,
        String model,
        long latencyMs
) {
}
