package com.sajo.market_service.market.controller;

import com.sajo.common.code.GeneralResponseCode;
import com.sajo.common.response.GeneralResponse;
import com.sajo.market_service.market.controller.dto.response.InternalStockIndicatorResponse;
import com.sajo.market_service.market.controller.dto.response.InternalStockQuoteResponse;
import com.sajo.market_service.market.service.query.MarketInternalQueryService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/internal/v1/stocks")
public class MarketInternalQueryController {

    // Internal API 접근 제어 및 X-User-Id 위조 방지는 Gateway/서비스 간 인증 정책 확정 후 별도 작업으로 처리한다.
    // 현재 상태에서는 운영 외부 노출이 허용되지 않는다.

    private final MarketInternalQueryService marketInternalQueryService;

    @GetMapping("/{stockCode}/indicator")
    public ResponseEntity<GeneralResponse<InternalStockIndicatorResponse>> getIndicator(
            @PathVariable @NotBlank @Pattern(regexp = "\\d{6}") String stockCode
    ) {
        return GeneralResponse.toResponseEntity(GeneralResponseCode.OK, marketInternalQueryService.getIndicator(stockCode));
    }

    @GetMapping("/{stockCode}/quote")
    public ResponseEntity<GeneralResponse<InternalStockQuoteResponse>> getQuote(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable @NotBlank @Pattern(regexp = "\\d{6}") String stockCode
    ) {
        return GeneralResponse.toResponseEntity(GeneralResponseCode.OK, marketInternalQueryService.getQuote(userId, stockCode));
    }
}
