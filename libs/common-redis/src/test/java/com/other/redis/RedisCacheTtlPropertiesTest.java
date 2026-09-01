package com.other.redis;

import com.other.TestApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCache;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = TestApplication.class,
        properties = {
                "sajo.redis.cache.ttl.default=10m",
                "sajo.redis.cache.ttl.item=5m"
        }
)
@DisplayName("캐시별 TTL 프로퍼티 바인딩 테스트")
class RedisCacheTtlPropertiesTest {

    @Autowired
    private CacheManager cacheManager;

    @Test
    @DisplayName("ttl 맵에 있는 캐시 이름은 그 캐시 이름에 지정된 TTL을 쓴다")
    void cacheNameWithConfiguredTtlUsesThatTtl() {
        RedisCache cache = (RedisCache) cacheManager.getCache("item");

        assertThat(cache.getCacheConfiguration().getTtlFunction().getTimeToLive(null, null))
                .isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("ttl 맵에 없는 캐시 이름은 default 키 값으로 fallback 한다")
    void cacheNameNotInTtlMapFallsBackToDefault() {
        RedisCache cache = (RedisCache) cacheManager.getCache("other");

        assertThat(cache.getCacheConfiguration().getTtlFunction().getTimeToLive(null, null))
                .isEqualTo(Duration.ofMinutes(10));
    }
}
