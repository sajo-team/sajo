package com.sajo.market_service.strategy.controller.dto.response;

import com.sajo.market_service.strategy.domain.Backtest;
import com.sajo.market_service.strategy.domain.BacktestStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BacktestSummaryResponse(
        UUID backtestId,
        String stockCode,
        LocalDate startDate,
        LocalDate endDate,
        BacktestStatus status,
        BigDecimal totalReturnRate,
        BigDecimal mdd,
        Instant requestedAt
) {
    public static BacktestSummaryResponse from(Backtest backtest) {
        return new BacktestSummaryResponse(
                backtest.getId(),
                backtest.getStockCode(),
                backtest.getStartDate(),
                backtest.getEndDate(),
                backtest.getStatus(),
                backtest.getTotalReturnRate(),
                backtest.getMdd(),
                backtest.getRequestedAt()
        );
    }
}
