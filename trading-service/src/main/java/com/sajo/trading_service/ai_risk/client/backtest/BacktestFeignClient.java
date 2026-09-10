package com.sajo.trading_service.ai_risk.client.backtest;

import com.sajo.trading_service.ai_risk.client.backtest.dto.BacktestInternalResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(
        name = "market-service",
        contextId = "backtestFeignClient"
)
public interface BacktestFeignClient {

    @GetMapping("/internal/v1/backtests/{backtestId}")
    BacktestInternalResponse getBacktest(
            @PathVariable("backtestId") UUID backtestId
    );
}
