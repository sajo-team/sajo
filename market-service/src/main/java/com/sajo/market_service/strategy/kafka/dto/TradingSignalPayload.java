package com.sajo.market_service.strategy.kafka.dto;


import java.util.UUID;

public record TradingSignalPayload(
        UUID signalId,
        UUID strategyId,
        UUID userId,
        String stockCode,
        SignalType signalType,
        Long triggerPrice,
        Long orderAmount,
        String signalReason
) {
}
