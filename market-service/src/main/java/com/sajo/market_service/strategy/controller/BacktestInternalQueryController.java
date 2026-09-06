package com.sajo.market_service.strategy.controller;

import com.sajo.market_service.strategy.controller.dto.response.BacktestInternalResponse;
import com.sajo.market_service.strategy.service.query.BacktestQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/backtests")
public class BacktestInternalQueryController {

    private final BacktestQueryService backtestQueryService;

    @GetMapping("/{backtestId}")
    public ResponseEntity<BacktestInternalResponse> getBacktestInternal(
            @PathVariable("backtestId") UUID backtestId
    ) {
        BacktestInternalResponse response = backtestQueryService.getBacktestInternal(backtestId);
        return ResponseEntity.ok(response);
    }
}
