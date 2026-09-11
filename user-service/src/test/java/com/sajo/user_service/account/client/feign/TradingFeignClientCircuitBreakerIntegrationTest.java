package com.sajo.user_service.account.client.feign;

import com.sajo.common.exception.BusinessException;
import com.sajo.user_service.account.exception.AccountErrorCode;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// trading-service가 Eureka에 등록돼 있지 않은 테스트 환경에서는 TradingFeignClient 호출이
// 항상 실패하므로, 이를 이용해 fallback/서킷브레이커 배선이 실제로 동작하는지 검증한다.
//
// src/test/resources/application.yaml과 src/main/resources/application.yaml은 둘 다
// classpath:/application.yaml로 같은 위치라서, 테스트 실행 시 main 쪽 설정(서킷브레이커 관련
// 설정 포함)이 로드되지 않는다 - 그래서 이 테스트가 필요로 하는 값만 properties로 직접 주입한다.
@SpringBootTest(properties = {
        "spring.cloud.openfeign.circuitbreaker.enabled=true",
        "resilience4j.circuitbreaker.configs.default.sliding-window-type=COUNT_BASED",
        "resilience4j.circuitbreaker.configs.default.sliding-window-size=10",
        "resilience4j.circuitbreaker.configs.default.minimum-number-of-calls=5",
        "resilience4j.circuitbreaker.configs.default.failure-rate-threshold=50",
        "resilience4j.circuitbreaker.configs.default.wait-duration-in-open-state=10s",
        "resilience4j.circuitbreaker.configs.default.permitted-number-of-calls-in-half-open-state=3"
})
class TradingFeignClientCircuitBreakerIntegrationTest {

    @Autowired
    private TradingFeignClient tradingFeignClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @AfterEach
    void resetCircuitBreaker() {
        findTradingCircuitBreaker().ifPresent(CircuitBreaker::reset);
    }

    @Test
    @DisplayName("trading-service를 찾을 수 없으면 원본 예외 대신 fallback이 계좌 삭제를 막는 예외를 던진다")
    void fallbackKicksInWhenTradingServiceUnavailable() {
        assertThatThrownBy(() -> tradingFeignClient.getActiveStatus(UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AccountErrorCode.TRADING_STATUS_CHECK_FAILED);
    }

    @Test
    @DisplayName("설정한 minimum-number-of-calls만큼 호출이 실패하면 서킷이 OPEN으로 전환된다")
    void circuitOpensAfterConfiguredFailureThreshold() {
        UUID userId = UUID.randomUUID();

        // application.yaml: resilience4j.circuitbreaker.configs.default.minimum-number-of-calls: 5
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> tradingFeignClient.getActiveStatus(userId))
                    .isInstanceOf(BusinessException.class);
        }

        CircuitBreaker breaker = findTradingCircuitBreaker()
                .orElseThrow(() -> new AssertionError("TradingFeignClient용 CircuitBreaker가 등록되지 않았습니다."));

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    private Optional<CircuitBreaker> findTradingCircuitBreaker() {
        return circuitBreakerRegistry.getAllCircuitBreakers().stream()
                .filter(cb -> cb.getName().contains("TradingFeignClient"))
                .findFirst();
    }
}
