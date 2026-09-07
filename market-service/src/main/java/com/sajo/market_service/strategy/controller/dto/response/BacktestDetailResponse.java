package com.sajo.market_service.strategy.controller.dto.response;

import com.sajo.market_service.strategy.domain.Backtest;
import com.sajo.market_service.strategy.domain.BacktestStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BacktestDetailResponse(
        UUID backtestId,
        UUID strategyId,
        String stockCode,
        LocalDate startDate,
        LocalDate endDate,
        Long initialCash,
        BacktestStatus status,
        BigDecimal totalReturnRate,
        BigDecimal mdd,
        BigDecimal winRate,
        Integer tradeCount,
        Integer maxConsecutiveLosses,
        Instant requestedAt
) {
    public static BacktestDetailResponse from(Backtest backtest) {
        return new BacktestDetailResponse(
                backtest.getId(),
                backtest.getStrategyId(),
                backtest.getStockCode(),
                backtest.getStartDate(),
                backtest.getEndDate(),
                backtest.getInitialCash(),
                backtest.getStatus(),
                backtest.getTotalReturnRate(),
                backtest.getMdd(),
                backtest.getWinRate(),
                backtest.getTradeCount(),
                backtest.getMaxConsecutiveLosses(),
                backtest.getRequestedAt()
        );
    }
}
