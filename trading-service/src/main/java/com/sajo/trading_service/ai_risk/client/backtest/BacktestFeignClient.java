package com.sajo.trading_service.ai_risk.client.backtest;

import com.sajo.common.response.GeneralResponse;
import com.sajo.trading_service.ai_risk.client.backtest.dto.BacktestInternalResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(
        name = "market-service",
        contextId = "backtestFeignClient",
        path = "/internal/v1/backtests"
)
public interface BacktestFeignClient {

    @GetMapping("/{backtestId}")
    GeneralResponse<BacktestInternalResponse> getBacktest(
            @PathVariable("backtestId") UUID backtestId
    );
}
