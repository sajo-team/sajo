package com.sajo.trading_service.trading.controller.internal;

import com.sajo.trading_service.trading.controller.dto.response.TradingActiveStatusResponse;
import com.sajo.trading_service.trading.service.query.TradingActiveStatusQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/trading")
public class TradingInternalController {

    private final TradingActiveStatusQueryService tradingActiveStatusQueryService;

    @GetMapping("/users/{userId}/active-status")
    public TradingActiveStatusResponse getActiveStatus(
            @PathVariable UUID userId
    ) {
        return tradingActiveStatusQueryService.getActiveStatus(userId);
    }
}