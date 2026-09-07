package com.sajo.market_service.strategy.service.command;

import com.sajo.market_service.strategy.domain.Strategy;

import java.math.BigDecimal;
import java.util.Objects;

public record StrategyActivationSnapshot(
        BigDecimal perCondition,
        BigDecimal pbrCondition
) {

    public static StrategyActivationSnapshot from(Strategy strategy) {
        return new StrategyActivationSnapshot(
                strategy.getPerCondition(),
                strategy.getPbrCondition()
        );
    }

    public boolean matches(Strategy strategy) {
        return Objects.equals(perCondition, strategy.getPerCondition())
                && Objects.equals(pbrCondition, strategy.getPbrCondition());
    }
}
