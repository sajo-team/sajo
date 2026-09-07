package com.sajo.market_service.strategy.controller;

import com.sajo.common.code.GeneralResponseCode;
import com.sajo.common.response.GeneralResponse;
import com.sajo.market_service.strategy.controller.dto.request.BacktestCreateRequest;
import com.sajo.market_service.strategy.controller.dto.response.BacktestCreateResponse;
import com.sajo.market_service.strategy.service.command.BacktestCommandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/strategies/{strategyId}/backtests")
public class BacktestCommandController {

    private final BacktestCommandService backtestCommandService;

    @PostMapping
    public ResponseEntity<GeneralResponse<BacktestCreateResponse>> createBacktest(
            @PathVariable("strategyId") UUID strategyId,
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody BacktestCreateRequest request
    ) {
        BacktestCreateResponse response = backtestCommandService.createBacktest(userId, strategyId, request);

        return GeneralResponse.toResponseEntity(GeneralResponseCode.CREATED, response);
    }
}
