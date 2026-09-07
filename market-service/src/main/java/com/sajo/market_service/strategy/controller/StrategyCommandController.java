package com.sajo.market_service.strategy.controller;

import com.sajo.common.code.GeneralResponseCode;
import com.sajo.common.response.GeneralResponse;
import com.sajo.market_service.strategy.controller.dto.request.StrategyActivationRequest;
import com.sajo.market_service.strategy.controller.dto.request.StrategyCreateRequest;
import com.sajo.market_service.strategy.controller.dto.request.StrategyUpdateRequest;
import com.sajo.market_service.strategy.controller.dto.response.StrategyActivationResponse;
import com.sajo.market_service.strategy.controller.dto.response.StrategyCreateResponse;
import com.sajo.market_service.strategy.controller.dto.response.StrategyUpdateResponse;
import com.sajo.market_service.strategy.service.command.StrategyCommandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/strategies")
public class StrategyCommandController {

    private final StrategyCommandService strategyCommandService;

    @PostMapping
    public ResponseEntity<GeneralResponse<StrategyCreateResponse>> createStrategy(
            @Valid @RequestBody StrategyCreateRequest request,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        StrategyCreateResponse response = strategyCommandService.createStrategy(userId, request);

        return GeneralResponse.toResponseEntity(GeneralResponseCode.CREATED, response);
    }

    @PatchMapping("/{strategyId}")
    public ResponseEntity<GeneralResponse<StrategyUpdateResponse>> updateStrategy(
            @PathVariable("strategyId") UUID strategyId,
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody StrategyUpdateRequest request
    ) {
        StrategyUpdateResponse response = strategyCommandService.updateStrategy(userId, strategyId, request);

        return GeneralResponse.toResponseEntity(GeneralResponseCode.OK, response);
    }

    @DeleteMapping("/{strategyId}")
    public ResponseEntity<GeneralResponse<Void>> deleteStrategy(
            @PathVariable("strategyId") UUID strategyId,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        strategyCommandService.deleteStrategy(userId, strategyId);

        return GeneralResponse.toResponseEntity(GeneralResponseCode.OK, null);
    }

    @PatchMapping("/{strategyId}/activation")
    public ResponseEntity<GeneralResponse<StrategyActivationResponse>> updateActivation(
            @PathVariable("strategyId") UUID strategyId,
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody StrategyActivationRequest request
    ) {
        StrategyActivationResponse response = strategyCommandService.updateActivation(userId, strategyId, request);

        return GeneralResponse.toResponseEntity(GeneralResponseCode.OK, response);
    }
}
