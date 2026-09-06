package com.sajo.market_service.strategy.controller.dto.response;

import com.sajo.market_service.strategy.domain.Backtest;
import com.sajo.market_service.strategy.domain.BacktestStatus;

import java.util.UUID;

public record BacktestStatusResponse(
        UUID backtestId,
        BacktestStatus status
) {
    public static BacktestStatusResponse from(Backtest backtest) {
        return new BacktestStatusResponse(
                backtest.getId(),
                backtest.getStatus()
        );
    }
}
