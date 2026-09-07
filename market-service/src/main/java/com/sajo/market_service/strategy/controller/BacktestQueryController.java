package com.sajo.market_service.strategy.controller;

import com.sajo.common.code.GeneralResponseCode;
import com.sajo.common.response.GeneralResponse;
import com.sajo.market_service.strategy.controller.dto.response.BacktestDetailResponse;
import com.sajo.market_service.strategy.controller.dto.response.BacktestListResponse;
import com.sajo.market_service.strategy.controller.dto.response.BacktestStatusResponse;
import com.sajo.market_service.strategy.service.query.BacktestQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/strategies/{strategyId}/backtests")
public class BacktestQueryController {

    private final BacktestQueryService backtestQueryService;

    @GetMapping("/{backtestId}/status")
    public ResponseEntity<GeneralResponse<BacktestStatusResponse>> getBacktestStatus(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable("strategyId") UUID strategyId,
            @PathVariable("backtestId") UUID backtestId
    ) {
        BacktestStatusResponse response = backtestQueryService.getBacktestStatus(userId, strategyId, backtestId);

        return GeneralResponse.toResponseEntity(GeneralResponseCode.OK, response);
    }

    @GetMapping("/{backtestId}")
    public ResponseEntity<GeneralResponse<BacktestDetailResponse>> getBacktestDetail(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable("strategyId") UUID strategyId,
            @PathVariable("backtestId") UUID backtestId
    ) {
        BacktestDetailResponse response = backtestQueryService.getBacktestDetail(userId, strategyId, backtestId);

        return GeneralResponse.toResponseEntity(GeneralResponseCode.OK, response);
    }

    @GetMapping
    public ResponseEntity<GeneralResponse<BacktestListResponse>> getBacktests(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable("strategyId") UUID strategyId,
            @PageableDefault(sort = "requestedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        BacktestListResponse response = backtestQueryService.getBacktests(userId, strategyId, pageable);

        return GeneralResponse.toResponseEntity(GeneralResponseCode.OK, response);
    }

}
