package com.sajo.market_service.market.controller;

import com.sajo.common.code.GeneralResponseCode;
import com.sajo.common.response.GeneralResponse;
import com.sajo.market_service.market.dto.response.QuoteResponse;
import com.sajo.market_service.market.dto.response.PublicQuoteResponse;
import com.sajo.market_service.market.service.command.MarketQuoteRequestLogCommandService;
import com.sajo.market_service.market.service.query.MarketQuoteQueryService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 현재가 조회
 */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/market")
public class MarketQuoteQueryController {

    private final MarketQuoteQueryService marketQuoteQueryService;
    private final MarketQuoteRequestLogCommandService marketQuoteRequestLogCommandService;

    @GetMapping("/quote")
    public ResponseEntity<GeneralResponse<PublicQuoteResponse>> getQuote(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam
            @NotBlank(message = "종목 코드는 필수입니다.")
            @Pattern(regexp = "\\d{6}", message = "종목 코드는 6자리 숫자여야 합니다.")
            String stockCode
    ) {
        QuoteResponse response = marketQuoteQueryService.getQuote(userId, stockCode);
        // 조회 이력 기록은 응답 경로와 무관한 부가 작업이라 Query 서비스에 섞지 않고, 별도 Command
        // 서비스에 위임한다(#248) — 발행 자체는 전용 executor로 위임되어 논블로킹이다.
        marketQuoteRequestLogCommandService.recordQuoteRequest(userId, stockCode);
        return GeneralResponse.toResponseEntity(GeneralResponseCode.OK, PublicQuoteResponse.from(response));
    }
}
