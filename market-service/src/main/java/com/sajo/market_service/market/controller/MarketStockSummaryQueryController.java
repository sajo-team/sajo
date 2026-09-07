package com.sajo.market_service.market.controller;

import com.sajo.common.code.GeneralResponseCode;
import com.sajo.common.response.GeneralResponse;
import com.sajo.market_service.market.dto.response.MarketStockSummaryResponse;
import com.sajo.market_service.market.service.query.MarketStockSummaryQueryService;
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
@RequestMapping("/api/v1/market/stocks")
public class MarketStockSummaryQueryController {

    private final MarketStockSummaryQueryService marketStockSummaryQueryService;

    @GetMapping("/{stockCode}/summary")
    public ResponseEntity<GeneralResponse<MarketStockSummaryResponse>> getSummary(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable
            @NotBlank
            @Pattern(regexp = "\\d{6}", message = "종목 코드는 6자리 숫자여야 합니다.")
            String stockCode
    ) {
        return GeneralResponse.toResponseEntity(
                GeneralResponseCode.OK, marketStockSummaryQueryService.getSummary(userId, stockCode));
    }
}
