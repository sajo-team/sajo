package com.sajo.user_service.account.client.feign;

import com.sajo.common.exception.BusinessException;
import com.sajo.user_service.account.exception.AccountErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

// 서킷 OPEN 또는 호출 실패 시 대신 호출됨. 계좌 삭제(돈과 관련된 작업)는 활성 거래 여부를
// 확인 못 한 채로 진행하면 안 되므로 fail-open(통과)이 아니라 fail-safe(차단)로 처리한다.
@Slf4j
@Component
public class TradingFeignClientFallbackFactory implements FallbackFactory<TradingFeignClient> {

    @Override
    public TradingFeignClient create(Throwable cause) {
        return userId -> {
            log.warn("trading-service 활성 거래 상태 확인 실패(서킷 OPEN 또는 호출 실패). userId={}", userId, cause);
            throw new BusinessException(AccountErrorCode.TRADING_STATUS_CHECK_FAILED);
        };
    }
}
