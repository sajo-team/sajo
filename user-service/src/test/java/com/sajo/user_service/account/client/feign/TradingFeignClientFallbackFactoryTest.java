package com.sajo.user_service.account.client.feign;

import com.sajo.common.exception.BusinessException;
import com.sajo.user_service.account.exception.AccountErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TradingFeignClientFallbackFactoryTest {

    private final TradingFeignClientFallbackFactory factory = new TradingFeignClientFallbackFactory();

    @Test
    @DisplayName("서킷 OPEN/호출 실패 시 활성 거래 여부를 알 수 없으므로, 통과(fail-open)가 아니라 계좌 삭제를 막는 예외를 던진다(fail-safe)")
    void createReturnsFallbackThatBlocksAccountDeletion() {
        TradingFeignClient fallback = factory.create(new RuntimeException("trading-service 응답 없음"));

        assertThatThrownBy(() -> fallback.getActiveStatus(UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AccountErrorCode.TRADING_STATUS_CHECK_FAILED);
    }

    @Test
    @DisplayName("원인 예외가 null이어도(방어적으로) 동일하게 계좌 삭제를 막는 예외를 던진다")
    void createHandlesNullCauseSafely() {
        TradingFeignClient fallback = factory.create(null);

        assertThatThrownBy(() -> fallback.getActiveStatus(UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AccountErrorCode.TRADING_STATUS_CHECK_FAILED);
    }
}
