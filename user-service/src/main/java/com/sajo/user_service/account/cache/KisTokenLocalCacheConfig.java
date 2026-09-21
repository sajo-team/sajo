package com.sajo.user_service.account.cache;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/*
    Redis(L2, 공유) 앞에 두는 인스턴스 로컬(L1) 캐시. AsyncCache로 두는 이유는
    KisTokenCacheQueryService가 asMap().putIfAbsent(...)로 future만 먼저 꽂아 동시 요청을 묶고,
    실제 로딩(Redis 조회 + 분산락 대기 + KIS 호출, 최대 수십 초)은 그 락 밖에서 요청 스레드가 직접
    수행하도록 하기 위함 - 동기 Cache.get(key, loader)를 쓰면 내부 ConcurrentHashMap 버킷 락이
    로더가 끝날 때까지 잡혀있어 무관한 다른 키까지 지연될 수 있다.
    KisTokenEntry(String, Duration)마다 TTL이 다르므로(access token은 KIS 응답 기준, approval key는
    고정 23시간) expireAfterWrite 대신 Expiry를 직접 구현한다.
*/
@Configuration
public class KisTokenLocalCacheConfig {


    @Bean
    public AsyncCache<String, KisTokenEntry> kisTokenEntryAsyncCache() {
        return Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfter(new Expiry<String, KisTokenEntry>() {
                    @Override
                    public long expireAfterCreate(String key, KisTokenEntry entry, long currentTime) {
                        return entry.ttl().toNanos();
                    }

                    @Override
                    public long expireAfterUpdate(
                            String key, KisTokenEntry entry, long currentTime, long currentDuration) {
                        return entry.ttl().toNanos();
                    }

                    @Override
                    public long expireAfterRead(
                            String key, KisTokenEntry entry, long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                })
                .buildAsync();
    }
}
