package com.sajo.market_service.strategy.controller.dto.response;

import com.sajo.market_service.strategy.domain.Backtest;
import com.sajo.market_service.strategy.domain.BacktestStatus;

import java.time.Instant;
import java.util.UUID;

public record BacktestCreateResponse(
        UUID backtestId,
        UUID strategyId,
        BacktestStatus status,
        Instant requestedAt
) {
    public static BacktestCreateResponse from(Backtest backtest) {
        return new BacktestCreateResponse(
                backtest.getId(),
                backtest.getStrategyId(),
                backtest.getStatus(),
                backtest.getRequestedAt()
        );
    }
}
