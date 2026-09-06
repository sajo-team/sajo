package com.sajo.market_service.strategy.controller.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record BacktestCreateRequest(
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull @Positive Long initialCash
) {
}
