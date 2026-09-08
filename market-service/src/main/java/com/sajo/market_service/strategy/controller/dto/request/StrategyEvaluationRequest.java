package com.sajo.market_service.strategy.controller.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.UUID;

public record StrategyEvaluationRequest(
        @NotNull
        // 같은 시세 이벤트가 중복 처리되는 것을 막기 위한 식별자
        UUID sourceEventId,

        @NotBlank
        @Pattern(regexp = "\\d{6}")
        String stockCode,

        @NotNull
        @Positive
        Long currentPrice,

        @NotNull
        Instant baseTime
) {
}
