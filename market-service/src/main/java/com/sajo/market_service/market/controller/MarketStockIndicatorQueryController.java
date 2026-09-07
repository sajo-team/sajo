package com.sajo.market_service.market.controller;

import com.sajo.common.code.GeneralResponseCode;
import com.sajo.common.response.GeneralResponse;
import com.sajo.market_service.market.dto.response.MarketStockIndicatorResponse;
import com.sajo.market_service.market.service.query.MarketStockIndicatorQueryService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 특정 종목의 가장 최신 투자지표를 조회
 */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/market/stocks")
public class MarketStockIndicatorQueryController {

    private final MarketStockIndicatorQueryService marketStockIndicatorQueryService;

    /**
     * 최신 투자지표 조회
     *
     * ex) 삼성전자의 가장 최신 투자지표를 보여줘
     *
     * @param stockCode
     * @return
     * 기준일
     * PER
     * PBR
     * EPS
     * BPS
     * ROE
     */
    @GetMapping("/{stockCode}/indicators")
    public ResponseEntity<GeneralResponse<MarketStockIndicatorResponse>> getLatestIndicator(
            @PathVariable @NotBlank @Pattern(regexp = "\\d{6}", message = "종목 코드는 6자리 숫자여야 합니다.") String stockCode
    ) {
        return GeneralResponse.toResponseEntity(
                GeneralResponseCode.OK, marketStockIndicatorQueryService.getLatestIndicator(stockCode));
    }
}
