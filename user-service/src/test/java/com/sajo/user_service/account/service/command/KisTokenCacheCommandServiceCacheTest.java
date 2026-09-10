package com.sajo.user_service.account.service.command;

import com.sajo.user_service.account.cache.KisTokenCacheKeys;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// 예전엔 @CacheEvict/@CachePut + ConcurrentMapCacheManager로 검증했지만, StringRedisTemplate 직접
// 사용으로 전환하면서 KisTokenCacheQueryService와 동일한 키를 실제로 읽고/지우는지 진짜 Redis로 검증한다.
@Testcontainers
@EnabledIfDockerAvailable
@SpringBootTest(classes = {KisTokenCacheCommandService.class, DataRedisAutoConfiguration.class})
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

    @Test
    @DisplayName("primeKisAccessTokenCache로 채워두면 KisTokenCacheQueryService가 읽는 것과 동일한 키에 값이 저장된다")
    void primeKisAccessTokenCacheStoresValueUnderSharedKey() {
        // given
        UUID userId = UUID.randomUUID();
        String primed = "primed-token";

        // when
        kisTokenCacheCommandService.primeKisAccessTokenCache(userId, primed, 86400L);

        // then
        assertThat(redisTemplate.opsForValue().get(KisTokenCacheKeys.accessToken(userId))).isEqualTo(primed);
    }

    @Test
    @DisplayName("expires_in이 안전마진보다 작아 TTL이 0이면 캐시에 저장하지 않는다")
    void primeKisAccessTokenCacheSkipsWhenTtlIsZero() {
        // given
        UUID userId = UUID.randomUUID();

        // when
        kisTokenCacheCommandService.primeKisAccessTokenCache(userId, "primed-token", 30L);

        // then
        assertThat(redisTemplate.opsForValue().get(KisTokenCacheKeys.accessToken(userId))).isNull();
    }

    @Test
    @DisplayName("evictKisTokenCaches를 호출하면 접근토큰/접속키 캐시가 둘 다 삭제된다")
    void evictKisTokenCachesRemovesBothCaches() {
        // given
        UUID userId = UUID.randomUUID();
        redisTemplate.opsForValue().set(KisTokenCacheKeys.accessToken(userId), "token");
        redisTemplate.opsForValue().set(KisTokenCacheKeys.approvalKey(userId), "approval");

        // when
        kisTokenCacheCommandService.evictKisTokenCaches(userId);

        // then
        assertThat(redisTemplate.opsForValue().get(KisTokenCacheKeys.accessToken(userId))).isNull();
        assertThat(redisTemplate.opsForValue().get(KisTokenCacheKeys.approvalKey(userId))).isNull();
    }
}
