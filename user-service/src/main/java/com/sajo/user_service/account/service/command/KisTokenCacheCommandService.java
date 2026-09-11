package com.sajo.user_service.account.service.command;

import com.sajo.user_service.account.cache.KisTokenCacheKeys;
import com.sajo.user_service.account.cache.KisTokenCacheTtl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KisTokenCacheCommandService {

    private final StringRedisTemplate redisTemplate;

    // 계좌 생성 시 검증차 이미 발급받은 토큰을 그대로 캐시에 채워 넣는다 (accessToken 문자열만 캐싱)
    // KisTokenCacheQueryService와 동일한 키/TTL 계산 기준을 써야 조회/삭제가 어긋나지 않는다
    public void primeKisAccessTokenCache(UUID userId, String accessToken, long expiresIn) {
        Duration ttl = KisTokenCacheTtl.calculate(expiresIn);
        if (ttl.isZero()) {
            return;
        }
        redisTemplate.opsForValue().set(KisTokenCacheKeys.accessToken(userId), accessToken, ttl);
    }

    // 계좌 삭제 시 접근토큰/접속키 캐시를 함께 제거한다
    public void evictKisTokenCaches(UUID userId) {
        redisTemplate.delete(KisTokenCacheKeys.accessToken(userId));
        redisTemplate.delete(KisTokenCacheKeys.approvalKey(userId));
    }
}
