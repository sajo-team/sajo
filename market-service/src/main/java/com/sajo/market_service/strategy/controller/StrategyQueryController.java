package com.sajo.market_service.strategy.controller;

import com.sajo.common.code.GeneralResponseCode;
import com.sajo.common.response.GeneralResponse;
import com.sajo.market_service.strategy.controller.dto.response.StrategyListResponse;
import com.sajo.market_service.strategy.domain.StrategyStatus;
import com.sajo.market_service.strategy.service.query.StrategyQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/strategies")
public class StrategyQueryController {

    private final StrategyQueryService strategyQueryService;

    @GetMapping
    public ResponseEntity<GeneralResponse<StrategyListResponse>> getStrategies(
            @RequestParam("userId") UUID userId,
            @RequestParam(required = false)StrategyStatus status,
            @RequestParam(required = false)String stockCode,
            Pageable pageable
    ) {
        StrategyListResponse response =
                strategyQueryService.getStrategies(
                        userId,
                        status,
                        stockCode,
                        pageable
                );

        return GeneralResponse.toResponseEntity(GeneralResponseCode.OK, response);
    }
}
