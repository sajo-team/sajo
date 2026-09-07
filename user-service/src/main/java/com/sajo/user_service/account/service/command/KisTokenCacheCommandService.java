package com.sajo.user_service.account.service.command;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.cache.annotation.CachePut;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class KisTokenCacheCommandService {

    // 계좌 생성 시 검증차 이미 발급받은 토큰을 그대로 캐시에 채워 넣는다 (accessToken 문자열만 캐싱)
    @CachePut(cacheNames = "kis-access-token", key = "#userId")
    public String primeKisAccessTokenCache(UUID userId, String accessToken) {
        return accessToken;
    }

    // 계좌 삭제 시 접근토큰/접속키 캐시를 함께 제거한다
    @Caching(evict = {
            @CacheEvict(cacheNames = "kis-access-token", key = "#userId"),
            @CacheEvict(cacheNames = "kis-approval-key", key = "#userId")
    })
    public void evictKisTokenCaches(UUID userId) {
    }
}
