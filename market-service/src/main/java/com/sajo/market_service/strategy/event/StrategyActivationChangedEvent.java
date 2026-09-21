package com.sajo.market_service.strategy.event;

import java.util.UUID;

public record StrategyActivationChangedEvent(UUID strategyId, String stockCode, boolean active) {
}
