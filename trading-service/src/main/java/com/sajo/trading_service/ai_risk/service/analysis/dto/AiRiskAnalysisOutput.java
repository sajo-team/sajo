package com.sajo.trading_service.ai_risk.service.analysis.dto;

public record AiRiskAnalysisOutput(
        AiRiskAnalysisResult result,
        String rawResponse,
        String promptContent,
        String promptVersion,
        String model,
        long latencyMs
) {
}
