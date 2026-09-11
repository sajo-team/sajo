package com.sajo.market_service.market.controller;

import com.sajo.common.code.GeneralResponseCode;
import com.sajo.common.response.GeneralResponse;
import com.sajo.market_service.market.dto.response.MarketStockIndicatorResponse;
import com.sajo.market_service.market.service.query.MarketStockIndicatorQueryService;
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
 * 특정 종목의 투자지표를 조회
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
     * ROE
     */
    @GetMapping("/{stockCode}/indicators")
    public ResponseEntity<GeneralResponse<MarketStockIndicatorResponse>> getLatestIndicator(
            @PathVariable @NotBlank @Pattern(regexp = "\\d{6}", message = "종목 코드는 6자리 숫자여야 합니다.") String stockCode
    ) {
        return GeneralResponse.toResponseEntity(
                GeneralResponseCode.OK, marketStockIndicatorQueryService.getLatestIndicator(stockCode));
    }

    /**
     * 투자지표 이력 조회
     *
     * ex) 삼성전자의 최근 투자지표 변화 추이 8건을 보여줘
     *
     * 신규 분기 스냅샷이 있으면 결산연월 기준, 없으면 레거시 기준일 기준으로 최대 limit건을 반환한다.
     * 두 축의 데이터는 하나의 응답에 섞이지 않는다. 지표가 전혀 없으면 빈 배열을 반환한다.
     *
     * @param stockCode 종목코드(6자리 숫자)
     * @param limit     조회 건수 (기본 8, 1~40)
     * @return 투자지표 이력(최신순)
     */
    @GetMapping("/{stockCode}/indicators/history")
    public ResponseEntity<GeneralResponse<List<MarketStockIndicatorResponse>>> getIndicatorHistory(
            @PathVariable @NotBlank @Pattern(regexp = "\\d{6}", message = "종목 코드는 6자리 숫자여야 합니다.") String stockCode,
            @RequestParam(defaultValue = "8") @Min(1) @Max(40) int limit
    ) {
        return GeneralResponse.toResponseEntity(
                GeneralResponseCode.OK, marketStockIndicatorQueryService.getIndicatorHistory(stockCode, limit));
    }
}
