package com.sajo.user_service.account.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.user_service.account.client.KisOAuthClient;
import com.sajo.user_service.account.client.feign.TradingFeignClient;
import com.sajo.user_service.account.client.feign.dto.response.TradingActiveStatusResponse;
import com.sajo.user_service.account.domain.Account;
import com.sajo.user_service.account.exception.AccountErrorCode;
import com.sajo.user_service.account.exception.KisBusinessException;
import com.sajo.user_service.account.service.query.KisTokenCacheQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountDeleteFacade {

    private final KisOAuthClient kisOAuthClient;
    private final KisTokenCacheQueryService cacheQueryService;
    private final KisTokenCacheCommandService cacheCommandService;
    private final AccountCommandService accountCommandService;
    private final KisTokenLogCommandService kisTokenLogCommandService;
    private final TradingFeignClient tradingClient;

    public void deleteAccount(UUID userId) {

        // trading-service에 활성 거래 여부를 확인하러 가기 전에 먼저 PENDING_DELETION으로
        // 표시한다 - 이 확인과 실제 삭제 사이의 짧은 창에 새 주문이 끼어들어도
        // AccountQueryService.getAccountByUserId가 이 계좌를 거부하게 된다.
        accountCommandService.markPendingDeletion(userId);

        // 활성화 된 자동매매 또는 체결 확정 안된 주문 있는지 확인
        TradingActiveStatusResponse response = tradingClient.getActiveStatus(userId);
        if (response.hasActiveTrading()) {
            accountCommandService.reactivate(userId);
            throw new BusinessException(AccountErrorCode.ACTIVE_TRADING_EXISTS);
        }

        // 계좌 삭제 (필수) - 실패하면 아무 부작용 없이 여기서 끝
        Account account = accountCommandService.deleteAccount(userId);

        // 이후는 best-effort: 실패해도 계좌 삭제 자체는 이미 끝난 상태
        // 폐기 시도 자체가 없었던 경우(token 미보유, 또는 캐시 조회 자체의 실패)는 기록하지 않는다 -
        Optional<String> token;
        try {
            token = cacheQueryService.peekAccessToken(userId);
        } catch (Exception e) {
            log.warn("계좌 삭제 시 캐시된 토큰 조회 실패. userId={}", userId, e);
            token = Optional.empty();
        }

        if (token.isPresent()) {
            try {
                kisOAuthClient.revokeAccessToken(
                        account.getAppKey(), account.getSecretKey(), token.get(), account.getAccountType());
                kisTokenLogCommandService.recordRevokeSuccess(account.getId(), userId);
            } catch (KisBusinessException e) {
                log.warn("계좌 삭제 시 KIS 토큰 폐기 실패. userId={}", userId, e);
                kisTokenLogCommandService.recordRevokeFail(
                        account.getId(), userId, e.getKisErrorCode(), e.getKisMessage());
            } catch (Exception e) {
                log.warn("계좌 삭제 시 KIS 토큰 폐기 실패. userId={}", userId, e);
                kisTokenLogCommandService.recordRevokeFail(account.getId(), userId, null, e.getMessage());
            }
        }

        // redis 캐시만 제거 - KIS는 접속키 폐기 API가 없어 실제 무효화는 안 됨.
        try {
            cacheCommandService.evictKisTokenCaches(userId);
        } catch (Exception e) {
            log.warn("계좌 삭제 시 KIS 토큰 캐시 제거 실패. userId={}", userId, e);
        }
    }
}
