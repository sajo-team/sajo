package com.sajo.market_service.strategy.controller;

import com.sajo.common.code.GeneralResponseCode;
import com.sajo.common.response.GeneralResponse;
import com.sajo.market_service.strategy.controller.dto.response.StrategyInternalResponse;
import com.sajo.market_service.strategy.service.query.StrategyQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/strategies")
public class StrategyInternalQueryController {

    private final StrategyQueryService strategyQueryService;

    @GetMapping("/{strategyId}")
    public ResponseEntity<GeneralResponse<StrategyInternalResponse>> getStrategyInternal(
            @PathVariable("strategyId") UUID strategyId
    ) {
        StrategyInternalResponse response = strategyQueryService.getStrategyInternal(strategyId);

        return GeneralResponse.toResponseEntity(GeneralResponseCode.OK, response);
    }
}
