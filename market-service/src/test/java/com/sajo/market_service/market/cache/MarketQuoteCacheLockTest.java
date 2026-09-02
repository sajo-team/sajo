package com.sajo.market_service.market.cache;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketQuoteCacheLockTest {

    @Test
    void acquiresLockWithTokenAndTtlAndReleasesItWithCompareAndDeleteScript() {
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        MarketQuoteCacheLock lock = new MarketQuoteCacheLock(stringRedisTemplate);
        Duration lockTtl = Duration.ofSeconds(30);

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("market:quote:lock:005930", "owner-token", lockTtl))
                .thenReturn(true);

        boolean acquired = lock.tryLock("005930", "owner-token", lockTtl);
        lock.unlock("005930", "owner-token");

        org.assertj.core.api.Assertions.assertThat(acquired).isTrue();
        verify(valueOperations).setIfAbsent("market:quote:lock:005930", "owner-token", lockTtl);
        verify(stringRedisTemplate).execute(
                any(RedisScript.class),
                eq(List.of("market:quote:lock:005930")),
                eq("owner-token")
        );
    }
}
