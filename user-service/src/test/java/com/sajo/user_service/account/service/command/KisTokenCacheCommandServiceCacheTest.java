package com.sajo.user_service.account.service.command;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.sajo.user_service.account.cache.KisTokenCacheKeys;
import com.sajo.user_service.account.cache.KisTokenCacheLock;
import com.sajo.user_service.account.cache.KisTokenEntry;
import com.sajo.user_service.account.cache.KisTokenLocalCache;
import com.sajo.user_service.account.cache.KisTokenLocalCacheConfig;
import com.sajo.user_service.account.cache.KisTokenRemoteCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@EnabledIfDockerAvailable
@SpringBootTest(classes = {
        KisTokenCacheCommandService.class, KisTokenRemoteCache.class, KisTokenLocalCache.class,
        KisTokenCacheLock.class, KisTokenLocalCacheConfig.class, DataRedisAutoConfiguration.class})
@DisplayName("KIS 토큰 캐시 커맨드 - 실제 Redis 통합 테스트")
class KisTokenCacheCommandServiceCacheTest {

    @Container
    @ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private KisTokenCacheCommandService kisTokenCacheCommandService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private AsyncCache<String, KisTokenEntry> kisTokenLocalCache;

    @Test
    @DisplayName("primeKisAccessTokenCache로 채워두면 KisTokenCacheQueryService가 읽는 것과 동일한 키에 값이 저장된다")
    void primeKisAccessTokenCacheStoresValueUnderSharedKey() {
        // given
        UUID accountId = UUID.randomUUID();
        String primed = "primed-token";

        // when
        kisTokenCacheCommandService.primeKisAccessTokenCache(accountId, primed, 86400L);

        // then
        assertThat(redisTemplate.opsForValue().get(KisTokenCacheKeys.accessToken(accountId))).isEqualTo(primed);
    }

    @Test
    @DisplayName("expires_in이 안전마진보다 작아 TTL이 0이면 캐시에 저장하지 않는다")
    void primeKisAccessTokenCacheSkipsWhenTtlIsZero() {
        // given
        UUID accountId = UUID.randomUUID();

        // when
        kisTokenCacheCommandService.primeKisAccessTokenCache(accountId, "primed-token", 30L);

        // then
        assertThat(redisTemplate.opsForValue().get(KisTokenCacheKeys.accessToken(accountId))).isNull();
    }

    @Test
    @DisplayName("evictKisTokenCaches를 호출하면 Redis(L2)와 로컬(L1)의 접근토큰/접속키 캐시가 모두 삭제된다")
    void evictKisTokenCachesRemovesBothCaches() {
        // given
        UUID accountId = UUID.randomUUID();
        String accessTokenKey = KisTokenCacheKeys.accessToken(accountId);
        String approvalKeyKey = KisTokenCacheKeys.approvalKey(accountId);
        redisTemplate.opsForValue().set(accessTokenKey, "token");
        redisTemplate.opsForValue().set(approvalKeyKey, "approval");
        kisTokenLocalCache.put(accessTokenKey, CompletableFuture.completedFuture(new KisTokenEntry("token", Duration.ofMinutes(1))));
        kisTokenLocalCache.put(approvalKeyKey, CompletableFuture.completedFuture(new KisTokenEntry("approval", Duration.ofMinutes(1))));

        // when
        kisTokenCacheCommandService.evictKisTokenCaches(accountId);

        // then
        assertThat(redisTemplate.opsForValue().get(accessTokenKey)).isNull();
        assertThat(redisTemplate.opsForValue().get(approvalKeyKey)).isNull();
        assertThat(kisTokenLocalCache.getIfPresent(accessTokenKey)).isNull();
        assertThat(kisTokenLocalCache.getIfPresent(approvalKeyKey)).isNull();
    }
}
