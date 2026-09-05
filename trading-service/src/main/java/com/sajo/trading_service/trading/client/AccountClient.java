package com.sajo.trading_service.trading.client;

import com.sajo.trading_service.trading.client.dto.response.AccountHoldingResponse;
import com.sajo.trading_service.trading.client.dto.response.AccountOrderInfoResponse;
import com.sajo.trading_service.trading.client.dto.response.AccountOrderableAmountResponse;
import com.sajo.trading_service.trading.client.dto.response.AccountTokenResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.UUID;

@FeignClient(name = "user-service")
public interface AccountClient {

    @PostMapping("/internal/v1/accounts/{userId}/token") // KIS 인증 정보 조회 (accessToken / appKey / secretKey)
    AccountTokenResponse getAccessToken(
            @PathVariable("userId") UUID userId
    );

    @GetMapping("/internal/v1/accounts/{userId}/order-info") // KIS 주문용 계좌 정보 조회 (cano / accountProductCode / accountType)
    AccountOrderInfoResponse getOrderInfo(
            @PathVariable("userId") UUID userId
    );

    @GetMapping("/internal/v1/accounts/{userId}/orderable-amount") // 매수 가능 금액 조회
    AccountOrderableAmountResponse getOrderableAmount(
            @PathVariable("userId") UUID userId
    );

    @GetMapping("/internal/v1/accounts/{userId}/holdings/{stockCode}") // 매도 가능 수량 조회
    AccountHoldingResponse getHolding(
            @PathVariable("userId") UUID userId,
            @PathVariable("stockCode") String stockCode
    );
}