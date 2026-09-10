package com.sajo.market_service.strategy.controller;

import com.sajo.market_service.strategy.controller.dto.request.StrategyEvaluationRequest;
import com.sajo.market_service.strategy.service.command.StrategyEvaluationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/strategies")
public class StrategyEvaluationInternalController {

    private final StrategyEvaluationService strategyEvaluationService;

    @PostMapping("/evaluations")
    public ResponseEntity<Void> evaluate(
            @Valid @RequestBody StrategyEvaluationRequest request
    ) {
        strategyEvaluationService.evaluate(request);

        return ResponseEntity.ok().build();
    }
}
