package com.sajo.trading_service.ai_risk.service.analysis.dto;

import com.sajo.trading_service.ai_risk.domain.RiskFactor;
import com.sajo.trading_service.ai_risk.domain.RiskLevel;

import java.util.List;

public record AiRiskAnalysisResult(
        RiskLevel riskLevel,
        String summary,
        List<RiskFactor> riskFactors,
        String reasoning,
        List<String> recommendations
) {
}
