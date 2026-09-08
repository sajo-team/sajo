package com.sajo.trading_service.trading.client;

import com.sajo.trading_service.trading.client.dto.response.StrategyClientResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "market-service")
public interface StrategyClient {

    @GetMapping("/internal/v1/strategies/{strategyId}")
    StrategyClientResponse getStrategy(
            @PathVariable UUID strategyId
    );
}
