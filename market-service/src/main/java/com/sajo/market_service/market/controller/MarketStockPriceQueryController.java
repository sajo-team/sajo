package com.sajo.market_service.market.controller;

import com.sajo.common.code.GeneralResponseCode;
import com.sajo.common.response.GeneralResponse;
import com.sajo.market_service.market.dto.response.MarketStockPriceResponse;
import com.sajo.market_service.market.service.query.MarketStockPriceQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 특정 종목의 최근 일별 시세 N건을 요청
 */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/market/stocks")
public class MarketStockPriceQueryController {

    private final MarketStockPriceQueryService marketStockPriceQueryService;

    /**
     * 최근 일별 시세 조회
     *
     * ex) 삼성전자의 최근 거래일 가격 30건을 보여줘
     *
     * @param stockCode
     * @param days
     * @return
     * 거래일
     * 시가
     * 고가
     * 저가
     * 종가
     * 누적 거래량
     * 누적 거래대금
     */
    @GetMapping("/{stockCode}/prices")
    public ResponseEntity<GeneralResponse<List<MarketStockPriceResponse>>> getRecentDailyPrices(
            @PathVariable @NotBlank @Pattern(regexp = "\\d{6}", message = "종목 코드는 6자리 숫자여야 합니다.") String stockCode,
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days
    ) {
        return GeneralResponse.toResponseEntity(
                GeneralResponseCode.OK, marketStockPriceQueryService.getRecentDailyPrices(stockCode, days));
    }
}
