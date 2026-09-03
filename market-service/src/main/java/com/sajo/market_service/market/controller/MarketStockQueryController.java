package com.sajo.market_service.market.controller;

import com.sajo.common.code.GeneralResponseCode;
import com.sajo.common.response.GeneralResponse;
import com.sajo.common.response.PageResponse;
import com.sajo.market_service.market.dto.response.MarketStockResponse;
import com.sajo.market_service.market.domain.MarketStock;
import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.market.exception.MarketErrorCode;
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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/market/stocks")
public class MarketStockQueryController {

    private final MarketStockQueryService marketStockQueryService;

    @GetMapping
    public ResponseEntity<GeneralResponse<PageResponse<MarketStockResponse>>> getStocks(
            @RequestParam(required = false) String marketType,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
            @RequestParam(required = false) @Pattern(regexp = "(?i)(stockCode|stockName|marketType)(,(asc|desc))?", message = "정렬 기준이 유효하지 않습니다.") String sort
    ) {
        return GeneralResponse.toResponseEntity(
                GeneralResponseCode.OK, marketStockQueryService.getStocks(marketType, page, size, sort));
    }

    @GetMapping("/search")
    public ResponseEntity<GeneralResponse<PageResponse<MarketStockResponse>>> searchStocks(
            @RequestParam @NotBlank(message = "검색어는 필수입니다.") @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
            @RequestParam(required = false) @Pattern(regexp = "(?i)(stockCode|stockName|marketType)(,(asc|desc))?", message = "정렬 기준이 유효하지 않습니다.") String sort
    ) {
        return GeneralResponse.toResponseEntity(
                GeneralResponseCode.OK, marketStockQueryService.searchStocks(keyword, page, size, sort));
    }

    @GetMapping("/{stockCode}")
    public ResponseEntity<GeneralResponse<MarketStockResponse>> getStock(
            @PathVariable @NotBlank(message = "종목 코드는 필수입니다.") String stockCode
    ) {
        return GeneralResponse.toResponseEntity(
                GeneralResponseCode.OK, marketStockQueryService.getStock(normalizePathStockCode(stockCode)));
    }

    private String normalizePathStockCode(String stockCode) {
        try {
            return MarketStock.normalizeStockCode(URLDecoder.decode(stockCode, StandardCharsets.UTF_8).trim());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(MarketErrorCode.INVALID_MARKET_STOCK, "종목 코드 형식이 유효하지 않습니다.");
        }
    }
}
