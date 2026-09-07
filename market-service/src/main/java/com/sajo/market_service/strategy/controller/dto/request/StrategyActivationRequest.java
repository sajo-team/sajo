package com.sajo.market_service.strategy.controller.dto.request;

import jakarta.validation.constraints.NotNull;

public record StrategyActivationRequest(
        @NotNull(message = "활성화 여부는 필수입니다.") Boolean active
) {
}
