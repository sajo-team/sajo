package com.sajo.trading_service.ai_risk.client.backtest.dto;

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
        String backtestStatus,
        BigDecimal totalReturnRate,
        BigDecimal mdd,
        BigDecimal winRate,
        Integer tradeCount,
        Integer maxConsecutiveLosses
) {
}
