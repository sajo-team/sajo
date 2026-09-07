package com.sajo.market_service.strategy.controller.dto.response;

import com.sajo.market_service.strategy.domain.Backtest;
import org.springframework.data.domain.Page;

import java.util.List;

public record BacktestListResponse(
        List<BacktestSummaryResponse> backtests,
        int page,
        int size,
        long totalElements
) {
    public static BacktestListResponse from(Page<Backtest> page) {
        return new BacktestListResponse(
                page.getContent().stream()
                        .map(BacktestSummaryResponse::from)
                        .toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        );
    }
}
