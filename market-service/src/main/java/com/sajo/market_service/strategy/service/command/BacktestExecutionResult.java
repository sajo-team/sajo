package com.sajo.market_service.strategy.service.command;

import java.math.BigDecimal;

public record BacktestExecutionResult(
        BigDecimal totalReturnRate,
        BigDecimal mdd,
        BigDecimal winRate,
        Integer tradeCount,
        Integer maxConsecutiveLosses
) {
}
