package com.sajo.user_service.account.client.feign;

import com.sajo.user_service.account.client.feign.dto.response.TradingActiveStatusResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

//TODO: circuit breaker 적용
@FeignClient(name = "trading-service")
public interface TradingFeignClient {

    @GetMapping("/internal/v1/trading/users/{userId}/active-status") // 활성 자동매매/미체결 주문 존재 여부 (계좌 삭제 검증용)
    TradingActiveStatusResponse getActiveStatus(
            @PathVariable("userId") UUID userId
    );
}
