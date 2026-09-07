package com.sajo.market_service.market.controller;

import com.sajo.common.code.GeneralResponseCode;
import com.sajo.common.response.GeneralResponse;
import com.sajo.market_service.market.dto.response.MarketDataStatusResponse;
import com.sajo.market_service.market.service.query.MarketDataStatusQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/market/data-status")
public class MarketDataStatusQueryController {

    private final MarketDataStatusQueryService marketDataStatusQueryService;

    @GetMapping
    public ResponseEntity<GeneralResponse<MarketDataStatusResponse>> getStatus() {
        return GeneralResponse.toResponseEntity(
                GeneralResponseCode.OK, marketDataStatusQueryService.getStatus());
    }
}
