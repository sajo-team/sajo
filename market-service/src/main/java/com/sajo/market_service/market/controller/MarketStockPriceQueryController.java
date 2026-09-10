package com.sajo.market_service.market.controller;

import com.sajo.common.code.GeneralResponseCode;
import com.sajo.common.exception.BusinessException;
import com.sajo.common.response.GeneralResponse;
import com.sajo.market_service.market.dto.response.MarketStockPriceResponse;
import com.sajo.market_service.market.exception.MarketErrorCode;
import com.sajo.market_service.market.service.query.MarketStockPriceQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 특정 종목의 일별 시세를 조회한다.
 *
 * startDate·endDate가 모두 주어지면 해당 기간의 일별 시세를 조회하고, days는 무시한다.
 * 그렇지 않으면 최근 거래일 기준 N건(days)을 조회한다.
 */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/market/stocks")
public class MarketStockPriceQueryController {

    private final MarketStockPriceQueryService marketStockPriceQueryService;

    /**
     * 일별 시세 조회
     *
     * ex) 삼성전자의 최근 거래일 가격 30건을 보여줘
     * ex) 삼성전자의 2026-08-01 ~ 2026-09-09 일별 시세를 보여줘
     *
     * @param stockCode  종목코드(6자리 숫자)
     * @param days       startDate·endDate가 없을 때 사용하는 최근 거래일 수 (기본 30, 1~365)
     * @param startDate  조회 시작일 (endDate와 함께 전달되어야 함)
     * @param endDate    조회 종료일 (startDate와 함께 전달되어야 함)
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
    public ResponseEntity<GeneralResponse<List<MarketStockPriceResponse>>> getDailyPrices(
            @PathVariable @NotBlank @Pattern(regexp = "\\d{6}", message = "종목 코드는 6자리 숫자여야 합니다.") String stockCode,
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        if ((startDate == null) != (endDate == null)) {
            throw new BusinessException(
                    MarketErrorCode.INVALID_MARKET_STOCK_PRICE,
                    "시작일과 종료일은 함께 전달되어야 합니다."
            );
        }

        List<MarketStockPriceResponse> response = (startDate != null)
                ? marketStockPriceQueryService.getDailyPrices(stockCode, startDate, endDate)
                : marketStockPriceQueryService.getRecentDailyPrices(stockCode, days);

        return GeneralResponse.toResponseEntity(GeneralResponseCode.OK, response);
    }
}
