package com.sajo.trading_service.ai_risk.domain;

import java.util.Objects;

public record RiskFactor(
        RiskFactorType type,
        String description
) {
}
