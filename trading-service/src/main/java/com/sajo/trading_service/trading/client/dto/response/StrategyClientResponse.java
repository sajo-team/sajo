package com.sajo.trading_service.trading.client.dto.response;

import java.util.UUID;

public record StrategyClientResponse(
        UUID strategyId,
        UUID userId
) {
}