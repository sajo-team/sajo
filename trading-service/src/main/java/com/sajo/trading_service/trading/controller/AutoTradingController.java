package com.sajo.trading_service.trading.controller;

import com.sajo.common.code.GeneralResponseCode;
import com.sajo.common.response.GeneralResponse;
import com.sajo.trading_service.trading.controller.dto.request.AutoTradingCreateRequest;
import com.sajo.trading_service.trading.controller.dto.request.AutoTradingUpdateRequest;
import com.sajo.trading_service.trading.controller.dto.response.AutoTradingCreateResponse;
import com.sajo.trading_service.trading.controller.dto.response.AutoTradingUpdateResponse;
import com.sajo.trading_service.trading.service.command.AutoTradingCommandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auto-tradings")
public class AutoTradingController {
    private final AutoTradingCommandService autoTradingCommandService;

    @PostMapping
    public ResponseEntity<GeneralResponse<AutoTradingCreateResponse>> createAutoTrading(
            @RequestParam("userId") UUID userId, // TODO: Gateway에서 JWT 검증 후 전달하는 X-User-Id 헤더를 사용하도록 변경
            @Valid @RequestBody AutoTradingCreateRequest request
    ){
        AutoTradingCreateResponse response =
                autoTradingCommandService.createAutoTrading(
                        userId,
                        request
                );
        return GeneralResponse.toResponseEntity(
                GeneralResponseCode.CREATED,
                response
        );
    }

    @PatchMapping("/{autoTradingId}")
    public ResponseEntity<GeneralResponse<AutoTradingUpdateResponse>> updateAutoTrading(
            @RequestParam("userId") UUID userId, // TODO: Gateway에서 JWT 검증 후 전달하는 X-User-Id 헤더를 사용하도록 변경
            @PathVariable("autoTradingId") UUID autoTradingId,
            @Valid @RequestBody AutoTradingUpdateRequest request
    ) {
        AutoTradingUpdateResponse response =
                autoTradingCommandService.updateAutoTrading(
                        userId,
                        autoTradingId,
                        request
                );

        return GeneralResponse.toResponseEntity(
                GeneralResponseCode.OK,
                response
        );
    }
}
