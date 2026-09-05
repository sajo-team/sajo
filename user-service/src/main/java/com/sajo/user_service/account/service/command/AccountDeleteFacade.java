package com.sajo.user_service.account.service.command;

import com.sajo.user_service.account.client.KisOAuthClient;
import com.sajo.user_service.account.domain.Account;
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

    public void deleteAccount(UUID userId) {
        // 계좌 삭제 (필수) - 실패하면 아무 부작용 없이 여기서 끝
        Account account = accountCommandService.deleteAccount(userId);

        // 이후는 best-effort: 실패해도 계좌 삭제 자체는 이미 끝난 상태
        try {
            Optional<String> token = cacheQueryService.peekAccessToken(userId);
            if (token.isPresent()) {
                kisOAuthClient.revokeAccessToken(
                        account.getAppKey(), account.getSecretKey(), token.get(), account.getAccountType());
            }
        } catch (Exception e) {
            log.warn("계좌 삭제 시 KIS 토큰 폐기 실패. userId={}", userId, e);
        }

        // redis 캐시만 제거 - KIS는 접속키 폐기 API가 없어 실제 무효화는 안 됨.
        try {
            cacheCommandService.evictKisTokenCaches(userId);
        } catch (Exception e) {
            log.warn("계좌 삭제 시 KIS 토큰 캐시 제거 실패. userId={}", userId, e);
        }
    }
}
