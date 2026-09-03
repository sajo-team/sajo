package com.sajo.market_service.strategy.controller;

import com.sajo.common.code.GeneralResponseCode;
import com.sajo.common.response.GeneralResponse;
import com.sajo.market_service.strategy.controller.dto.response.StrategyDetailResponse;
import com.sajo.market_service.strategy.controller.dto.response.StrategyListResponse;
import com.sajo.market_service.strategy.domain.StrategyStatus;
import com.sajo.market_service.strategy.service.query.StrategyQueryService;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/strategies")
public class StrategyQueryController {

    private final StrategyQueryService strategyQueryService;

    @GetMapping
    public ResponseEntity<GeneralResponse<StrategyListResponse>> getStrategies(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(required = false)StrategyStatus status,
            @RequestParam(required = false) @Pattern(regexp = "\\d{6}", message = "종목 코드는 6자리 숫자여야 합니다.") String stockCode,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
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

    @GetMapping("/{strategyId}")
    public ResponseEntity<GeneralResponse<StrategyDetailResponse>> getStrategy(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID strategyId
    ) {
        StrategyDetailResponse response = strategyQueryService.getStrategy(userId, strategyId);

        return GeneralResponse.toResponseEntity(GeneralResponseCode.OK, response);
    }
}
