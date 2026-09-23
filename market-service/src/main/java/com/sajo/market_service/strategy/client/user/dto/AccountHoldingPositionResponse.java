package com.sajo.market_service.strategy.client.user.dto;

import java.math.BigDecimal;

public record AccountHoldingPositionResponse(
        Long quantity,
        BigDecimal avgPurchasePrice,
        BigDecimal profitLossRate
) {
}
