package com.sajo.market_service.strategy.client.user;

import com.sajo.market_service.strategy.client.user.dto.AccountHoldingPositionResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/**
 * user-service(Account 도메인)의 종목별 보유 포지션 조회 internal API 클라이언트.
 * market.client.user.UserAccountFeignClient와 동일 서비스(user-service)를 호출하지만
 * strategy 패키지 소유이므로 별도 인터페이스로 분리한다. 같은 name("user-service")의 기존
 * Feign 클라이언트와 contextId가 겹치지 않도록 명시적으로 지정한다.
 */
@FeignClient(name = "user-service", contextId = "accountHoldingFeignClient")
public interface AccountHoldingFeignClient {

    @GetMapping("/internal/v1/accounts/{userId}/holdings/{stockCode}/position")
    AccountHoldingPositionResponse getHoldingPosition(
            @PathVariable("userId") UUID userId,
            @PathVariable("stockCode") String stockCode
    );
}
