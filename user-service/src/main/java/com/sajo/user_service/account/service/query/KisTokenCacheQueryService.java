package com.sajo.user_service.account.service.query;

import com.sajo.user_service.account.client.kis.KisOAuthClient;
import com.sajo.user_service.account.domain.AccountType;
import com.sajo.user_service.account.domain.KisTokenType;
import com.sajo.user_service.account.exception.KisBusinessException;
import com.sajo.user_service.account.service.command.KisTokenLogCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KisTokenCacheQueryService {

    private static final String ACCESS_TOKEN_CACHE = "kis-access-token";

    private final KisOAuthClient kisOAuthClient;
    private final CacheManager cacheManager;
    private final KisTokenLogCommandService kisTokenLogCommandService;

    // Redis에는 accessToken 문자열만 캐싱한다
    // ToDo : 캐시 만료 시 kis 요청 방지 위해 분산락 적용
    //  @Cacheable -> RedisTemplate 직접 사용으로 전환하면서,
    //  KIS 응답의 expires_in 기준으로 TTL을 동적으로 계산하도록 같이 개선
    @Cacheable(cacheNames = ACCESS_TOKEN_CACHE, key = "#userId", sync = true)
    public String getAccessToken(UUID userId, UUID accountId, String appKey, String secretKey, AccountType accountType) {
        try {
            String accessToken = kisOAuthClient.getAccessToken(appKey, secretKey, accountType).access_token();
            kisTokenLogCommandService.recordSuccess(accountId, userId, KisTokenType.ACCESS_TOKEN);
            return accessToken;
        } catch (KisBusinessException e) {
            kisTokenLogCommandService.recordFail(
                    accountId, userId, KisTokenType.ACCESS_TOKEN, e.getKisErrorCode(), e.getKisMessage());
            throw e;
        }
    }

    // 캐시에 이미 있는 값만 확인한다 - 캐시 미스여도 KIS를 호출해 새로 발급받지 않는다
    // (계좌 삭제 시 "폐기할 토큰이 있으면 폐기"하려는 용도라, 없는데 새로 발급받아 폐기하는 건 의미가 없음)
    public Optional<String> peekAccessToken(UUID userId) {
        Cache cache = cacheManager.getCache(ACCESS_TOKEN_CACHE);
        if (cache == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(cache.get(userId, String.class));
    }

    // ToDo : 캐시 만료 시 kis 중복 요청 방지 위해 분산락 적용 (접근토큰과 동일한 이슈)
    @Cacheable(cacheNames = "kis-approval-key", key = "#userId", sync = true)
    public String getApprovalKey(UUID userId, UUID accountId, String appKey, String secretKey, AccountType accountType) {
        try {
            String approvalKey = kisOAuthClient.getApprovalKey(appKey, secretKey, accountType).approval_key();
            kisTokenLogCommandService.recordSuccess(accountId, userId, KisTokenType.APPROVAL_KEY);
            return approvalKey;
        } catch (KisBusinessException e) {
            kisTokenLogCommandService.recordFail(
                    accountId, userId, KisTokenType.APPROVAL_KEY, e.getKisErrorCode(), e.getKisMessage());
            throw e;
        }
    }
}
