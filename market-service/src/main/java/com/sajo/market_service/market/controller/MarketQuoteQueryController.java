package com.sajo.market_service.market.controller;

import com.sajo.common.code.GeneralResponseCode;
import com.sajo.common.response.GeneralResponse;
import com.sajo.market_service.market.dto.response.QuoteResponse;
import com.sajo.market_service.market.dto.response.PublicQuoteResponse;
import com.sajo.market_service.market.service.query.MarketQuoteQueryService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 현재가 조회
 */
@RestController
@Validated
@RequiredArgsConstructor
public class MarketQuoteQueryController {

    private final MarketQuoteQueryService marketQuoteQueryService;

    @GetMapping("/quote")
    public ResponseEntity<GeneralResponse<PublicQuoteResponse>> getQuote(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam
            @NotBlank(message = "종목 코드는 필수입니다.")
            @Pattern(regexp = "\\d{6}", message = "종목 코드는 6자리 숫자여야 합니다.")
            String stockCode
    ) {
        QuoteResponse response = marketQuoteQueryService.getQuote(userId, stockCode);
        return GeneralResponse.toResponseEntity(GeneralResponseCode.OK, PublicQuoteResponse.from(response));
    }
}
