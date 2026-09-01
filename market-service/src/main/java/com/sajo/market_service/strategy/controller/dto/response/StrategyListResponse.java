package com.sajo.market_service.strategy.controller.dto.response;

import com.sajo.market_service.strategy.domain.Strategy;
import org.springframework.data.domain.Page;

import java.util.List;

public record StrategyListResponse(
        List<StrategySummaryResponse> strategies,
        int page,
        int size,
        long totalElements
) {
    public static StrategyListResponse from(Page<Strategy> strategies) {
        return new StrategyListResponse(
                strategies.getContent().stream()
                            .map(StrategySummaryResponse::from)
                            .toList(),
                strategies.getNumber(),
                strategies.getSize(),
                strategies.getTotalElements()
        );
    }
}
