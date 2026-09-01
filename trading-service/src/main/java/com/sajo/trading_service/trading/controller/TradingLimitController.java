package com.sajo.trading_service.trading.controller;

import com.sajo.common.code.GeneralResponseCode;
import com.sajo.common.response.GeneralResponse;
import com.sajo.trading_service.trading.controller.dto.request.TradingLimitCreateRequest;
import com.sajo.trading_service.trading.controller.dto.request.TradingLimitUpdateRequest;
import com.sajo.trading_service.trading.controller.dto.response.TradingLimitCreateResponse;
import com.sajo.trading_service.trading.controller.dto.response.TradingLimitQueryResponse;
import com.sajo.trading_service.trading.controller.dto.response.TradingLimitUpdateResponse;
import com.sajo.trading_service.trading.service.command.TradingLimitCommandService;
import com.sajo.trading_service.trading.service.query.TradingLimitQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/trading-limits")
public class TradingLimitController {
    private final TradingLimitCommandService tradingLimitCommandService;
    private final TradingLimitQueryService tradingLimitQueryService;

    @PostMapping
    public ResponseEntity<GeneralResponse<TradingLimitCreateResponse>> createTradingLimit(
            @Valid @RequestBody TradingLimitCreateRequest request,
            @RequestParam("userId") UUID userId // TODO: Gateway에서 JWT 검증 후 전달하는 X-User-Id 헤더를 사용하도록 변경
    ) {
        TradingLimitCreateResponse response =
                tradingLimitCommandService.createTradingLimit(userId, request);

        return GeneralResponse.toResponseEntity(
                GeneralResponseCode.CREATED,
                response
        );
    }

    @GetMapping
    public ResponseEntity<GeneralResponse<TradingLimitQueryResponse>> getTradingLimit(
            @RequestParam("userId") UUID userId // TODO: Gateway에서 JWT 검증 후 전달하는 X-User-Id 헤더를 사용하도록 변경
    ){
        TradingLimitQueryResponse response =
                tradingLimitQueryService.findByUserId(userId);

        return GeneralResponse.toResponseEntity(
                GeneralResponseCode.OK,
                response
        );
    }

    @PatchMapping
    public ResponseEntity<GeneralResponse<TradingLimitUpdateResponse>> updateTradingLimit(
            @RequestParam("userId") UUID userId, // TODO: Gateway에서 JWT 검증 후 전달하는 X-User-Id 헤더를 사용하도록 변경
            @Valid @RequestBody TradingLimitUpdateRequest request
    ){
        TradingLimitUpdateResponse response =
                tradingLimitCommandService.updateTradingLimit(userId, request);

        return GeneralResponse.toResponseEntity(
                GeneralResponseCode.OK,
                response
        );
    }
}
