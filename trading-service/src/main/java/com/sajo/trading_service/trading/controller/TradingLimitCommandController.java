package com.sajo.trading_service.trading.controller;

import com.sajo.common.code.GeneralResponseCode;
import com.sajo.common.response.GeneralResponse;
import com.sajo.trading_service.trading.controller.dto.request.TradingLimitCreateRequest;
import com.sajo.trading_service.trading.controller.dto.response.TradingLimitCreateResponse;
import com.sajo.trading_service.trading.service.command.TradingLimitCommandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/trading-limits")
public class TradingLimitCommandController {
    private final TradingLimitCommandService tradingLimitCommandService;

    @PostMapping
    public ResponseEntity<GeneralResponse<TradingLimitCreateResponse>> createTradingLimit(
            @Valid @RequestBody TradingLimitCreateRequest request,
            UUID userId // TODO: 인증 방식 확정 후 로그인 사용자 ID 주입
    ) {
        TradingLimitCreateResponse response =
                tradingLimitCommandService.createTradingLimit(userId, request);

        return GeneralResponse.toResponseEntity(
                GeneralResponseCode.CREATED,
                response
        );
    }
}
