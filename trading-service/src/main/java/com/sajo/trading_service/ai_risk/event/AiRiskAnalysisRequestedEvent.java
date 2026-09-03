package com.sajo.trading_service.ai_risk.event;

import com.sajo.trading_service.ai_risk.client.backtest.dto.BacktestInternalResponse;
import com.sajo.trading_service.ai_risk.client.strategy.dto.StrategyInternalResponse;

import java.util.UUID;

public record AiRiskAnalysisRequestedEvent(
        UUID analysisId,
        StrategyInternalResponse strategy,
        BacktestInternalResponse backtest
) {
}
