package com.sajo.trading_service.trading.client.dto.response;

import com.sajo.trading_service.trading.domain.enums.StrategyStatus;

import java.util.UUID;

public record StrategyClientResponse(
        UUID strategyId,
        UUID userId,
        StrategyStatus status
) {
}