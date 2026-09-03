package com.sajo.trading_service.ai_risk.client.strategy;


import com.sajo.common.response.GeneralResponse;
import com.sajo.trading_service.ai_risk.client.strategy.dto.StrategyInternalResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(
        name = "market-service",
        contextId = "strategyFeignClient",
        path = "/internal/v1/strategies"
)
public interface StrategyFeignClient {

    @GetMapping("/{strategyId}")
    GeneralResponse<StrategyInternalResponse> getStrategy(
            @PathVariable("strategyId") UUID strategyId
    );
}
