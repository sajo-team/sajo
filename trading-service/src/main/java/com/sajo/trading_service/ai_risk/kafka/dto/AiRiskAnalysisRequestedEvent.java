package com.sajo.trading_service.ai_risk.kafka.dto;

import com.sajo.trading_service.ai_risk.client.backtest.dto.BacktestInternalResponse;
import com.sajo.trading_service.ai_risk.client.strategy.dto.StrategyInternalResponse;

import java.time.Instant;
import java.util.UUID;

public record AiRiskAnalysisRequestedEvent(
        UUID eventId,
        String eventType,
        Integer eventVersion,
        Instant occurredAt,
        UUID actorId,
        Payload payload
) {

    public static final String EVENT_TYPE = "AI_RISK_ANALYSIS_REQUESTED";
    public  static final int EVENT_VERSION = 1;

    public record Payload(
            UUID analysisId,
            StrategyInternalResponse strategy,
            BacktestInternalResponse backtest
    ){
    }

    public static AiRiskAnalysisRequestedEvent of(
            UUID actorId,
            UUID analysisId,
            StrategyInternalResponse strategy,
            BacktestInternalResponse backtest
    ){
        return new AiRiskAnalysisRequestedEvent(
                UUID.randomUUID(),
                EVENT_TYPE,
                EVENT_VERSION,
                Instant.now(),
                actorId,
                new Payload(
                        analysisId,
                        strategy,
                        backtest
                )
        );
    }
}
