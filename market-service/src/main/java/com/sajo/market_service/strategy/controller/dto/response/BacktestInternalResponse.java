package com.sajo.market_service.strategy.controller.dto.response;

import com.sajo.market_service.strategy.domain.Backtest;
import com.sajo.market_service.strategy.domain.BacktestStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record BacktestInternalResponse(
        UUID backtestId,
        UUID strategyId,
        UUID userId,
        String stockCode,
        LocalDate startDate,
        LocalDate endDate,
        Long initialCash,
        BacktestStatus backtestStatus,
        BigDecimal totalReturnRate,
        BigDecimal mdd,
        BigDecimal winRate,
        Integer tradeCount,
        Integer maxConsecutiveLosses
) {
    public static BacktestInternalResponse from(Backtest backtest) {
        return new BacktestInternalResponse(
                backtest.getId(),
                backtest.getStrategyId(),
                backtest.getUserId(),
                backtest.getStockCode(),
                backtest.getStartDate(),
                backtest.getEndDate(),
                backtest.getInitialCash(),
                backtest.getStatus(),
                backtest.getTotalReturnRate(),
                backtest.getMdd(),
                backtest.getWinRate(),
                backtest.getTradeCount(),
                backtest.getMaxConsecutiveLosses()
        );
    }
}
