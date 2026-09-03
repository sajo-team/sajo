package com.sajo.market_service.market.controller;

import com.sajo.common.code.GeneralResponseCode;
import com.sajo.common.response.GeneralResponse;
import com.sajo.common.response.PageResponse;
import com.sajo.market_service.market.dto.response.MarketStockResponse;
import com.sajo.market_service.market.domain.MarketStock;
import com.sajo.market_service.market.service.query.MarketStockQueryService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 종목 목록 조회
 */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/market/stocks")
public class MarketStockQueryController {

    private final MarketStockQueryService marketStockQueryService;

    /**
     * 종목 목록 조회
     *
     * @param marketType
     * @param page
     * @param size
     * @param sort
     * @return
     * 삼성전자
     * SK하이닉스
     * NAVER
     * 현대차
     */
    @GetMapping
    public ResponseEntity<GeneralResponse<PageResponse<MarketStockResponse>>> getStocks(
            @RequestParam(required = false) String marketType,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
            @RequestParam(required = false) @Pattern(regexp = "(stockCode|stockName|marketType)(,(asc|desc))?", message = "정렬 기준이 유효하지 않습니다.") String sort
    ) {
        return GeneralResponse.toResponseEntity(
                GeneralResponseCode.OK, marketStockQueryService.getStocks(marketType, page, size, sort));
    }

    /**
     * 종목 검색
     *
     * ex) 이름이나 코드에 ‘삼성’이 포함된 종목 찾기
     */
    @GetMapping("/search")
    public ResponseEntity<GeneralResponse<PageResponse<MarketStockResponse>>> searchStocks(
            @RequestParam @NotBlank(message = "검색어는 필수입니다.") @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
            @RequestParam(required = false) @Pattern(regexp = "(stockCode|stockName|marketType)(,(asc|desc))?", message = "정렬 기준이 유효하지 않습니다.") String sort
    ) {
        return GeneralResponse.toResponseEntity(
                GeneralResponseCode.OK, marketStockQueryService.searchStocks(keyword, page, size, sort));
    }

    /**
     * 종목 기본정보 조회
     *
     * ex) 005930 종목이 무엇인지 알려줘
     *
     * @param stockCode
     * @return
     * 종목코드: 005930
     * 종목명: 삼성전자
     * 시장: KOSPI
     * 업종 코드
     * 상장 주식 수
     * 시가총액
     */
    @GetMapping("/{stockCode}")
    public ResponseEntity<GeneralResponse<MarketStockResponse>> getStock(
            @PathVariable @NotBlank(message = "종목 코드는 필수입니다.") String stockCode
    ) {
        return GeneralResponse.toResponseEntity(
                GeneralResponseCode.OK, marketStockQueryService.getStock(normalizePathStockCode(stockCode)));
    }

    private String normalizePathStockCode(String stockCode) {
        return MarketStock.normalizeStockCode(stockCode);
    }
}
