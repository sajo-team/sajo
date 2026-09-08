package com.sajo.market_service.strategy.service.command;

import java.math.BigDecimal;

public record BacktestExecutionResult(
        BigDecimal totalReturnRate,
        Integer tradeCount
) {
}
