package com.sajo.user_service.account.service.command;

import com.sajo.user_service.account.cache.KisTokenCacheKeys;
import com.sajo.user_service.account.cache.KisTokenCacheTtl;
import com.sajo.user_service.account.cache.KisTokenEntry;
import com.sajo.user_service.account.cache.KisTokenLocalCache;
import com.sajo.user_service.account.cache.KisTokenRemoteCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KisTokenCacheCommandService {

    private final KisTokenRemoteCache remoteCache;
    private final KisTokenLocalCache localCache;

    // 계좌 생성 시 검증차 이미 발급받은 토큰을 그대로 캐시에 채워 넣는다 (accessToken 문자열만 캐싱)
    // Redis(L2)만 채워도 충분하다 - 직후 첫 조회 때 KisTokenLocalCache.getOrLoad가 L2 히트를
    // 그대로 L1에도 채워 넣기 때문에, 여기서 L1까지 직접 건드릴 필요는 없다.
    public void primeKisAccessTokenCache(UUID accountId, String accessToken, long expiresIn) {
        Duration ttl = KisTokenCacheTtl.calculate(expiresIn);
        if (ttl.isZero()) {
            return;
        }
        remoteCache.save(KisTokenCacheKeys.accessToken(accountId), new KisTokenEntry(accessToken, ttl));
    }

    // 계좌 삭제 시 접근토큰/접속키 캐시를 Redis(L2)와 로컬(L1) 양쪽 다 제거한다.
    // L1은 인스턴스 로컬이라 이 요청을 처리한 인스턴스의 캐시만 지워지지만, 캐시 키가 accountId
    // 기준이라 재연동 시 새 accountId로 자연스럽게 분리되므로 다른 인스턴스에 남은 옛 항목이
    // 새 계좌 요청에 쓰일 위험은 없다.
    public void evictKisTokenCaches(UUID accountId) {
        String accessTokenKey = KisTokenCacheKeys.accessToken(accountId);
        String approvalKeyKey = KisTokenCacheKeys.approvalKey(accountId);

        remoteCache.evict(accessTokenKey);
        remoteCache.evict(approvalKeyKey);
        localCache.evict(accessTokenKey);
        localCache.evict(approvalKeyKey);
    }
}
