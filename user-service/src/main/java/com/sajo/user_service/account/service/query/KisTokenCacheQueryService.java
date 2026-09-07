package com.sajo.user_service.account.service.query;

import com.sajo.user_service.account.client.KisOAuthClient;
import com.sajo.user_service.account.domain.AccountType;
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

    // Redis에는 accessToken 문자열만 캐싱한다
    // ToDo : 캐시 만료 시 kis 요청 방지 위해 분산락 적용
    //  @Cacheable -> RedisTemplate 직접 사용으로 전환하면서,
    //  KIS 응답의 expires_in 기준으로 TTL을 동적으로 계산하도록 같이 개선
    @Cacheable(cacheNames = ACCESS_TOKEN_CACHE, key = "#userId", sync = true)
    public String getAccessToken(UUID userId, String appKey, String secretKey, AccountType accountType) {
        return kisOAuthClient.getAccessToken(appKey, secretKey, accountType).access_token();
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
    public String getApprovalKey(UUID userId, String appKey, String secretKey, AccountType accountType) {
        return kisOAuthClient.getApprovalKey(appKey, secretKey, accountType).approval_key();
    }
}
