package com.sajo.market_service.market.client.user;

import com.sajo.market_service.market.client.user.dto.UserKisTokenResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.UUID;

/**
 * {@link com.sajo.market_service.market.websocket.KisWebSocketClient} 전용 Feign 클라이언트.
 *
 * <p>{@link UserAccountFeignClient}와 동일한 user-service {@code /internal/v1/accounts/{userId}/token}
 * 엔드포인트를 호출하지만, contextId를 분리해서 별도의 타임아웃 설정
 * ({@code spring.cloud.openfeign.client.config.kisWebSocketUserAccountFeignClient})을 적용한다.
 * KisWebSocketClient는 재연결 전용 단일 스레드 위에서 이 호출을 동기적으로 수행하므로 짧은 타임아웃이
 * 필요하지만, {@code MarketQuoteQueryService} 등 실사용자 트래픽을 받는 다른 호출부까지 같은 짧은
 * 타임아웃의 영향을 받지 않도록 하기 위함이다.</p>
 */
@FeignClient(name = "user-service", contextId = "kisWebSocketUserAccountFeignClient")
public interface KisWebSocketUserAccountFeignClient {

    @PostMapping("/internal/v1/accounts/{userId}/token")
    UserKisTokenResponse getKisToken(@PathVariable("userId") UUID userId);
}
